package mcp

import BotNotifier
import CheckInService
import HabitService
import Lang
import Strings
import UserSettingsService
import dev.inmo.kslog.common.KSLog
import dev.inmo.kslog.common.info
import dto.CheckinRecordArgs
import dto.CheckinStatus
import dto.HabitType
import dto.McpJson
import dto.McpProp
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.decodeFromJsonElement
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

private val TimeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

object CheckinRecordTool : McpTool {
    override val name = "checkin_record"
    override val description =
        "Record a check-in. counter: value is an integer count 1..100 (default 1). quantity (single-field): value is the amount (>0); optional comment attaches a free-form note. For multi-field quantity habits prefer 'quantity_group_record' — it writes all fields with one shared comment in a single call; this tool will reject the group root's habitId. scheduled: status is 'done' (default) or 'skip'; if the habit has more than one reminder, pass reminderTime (HH:MM). Date is optional (YYYY-MM-DD), defaults to today in the user's timezone. Future dates are rejected; for scheduled habits, the reminder slot must have already fired today. 'comment' is only allowed for quantity habits."
    override val inputSchema: ToolSchema = toolSchema(
        mapOf(
            "habitId" to McpProp(type = "integer"),
            "value" to McpProp(type = "number", exclusiveMinimum = 0),
            "date" to McpProp(type = "string", pattern = "^\\d{4}-\\d{2}-\\d{2}$"),
            "reminderTime" to McpProp(type = "string", pattern = "^[0-2][0-9]:[0-5][0-9]$"),
            "status" to McpProp(type = "string", enum = listOf("done", "skip")),
            "comment" to McpProp(type = "string"),
        ),
        required = listOf("habitId"),
    )

    override fun handle(userId: Long, lang: Lang, request: CallToolRequest): CallToolResult {
        val rawArgs = request.arguments ?: return failed(userId, name, null, "arguments required")
        logCall(userId, name, rawArgs)
        return try {
            val args = runCatching { McpJson.decodeFromJsonElement<CheckinRecordArgs>(rawArgs) }
                .getOrElse { return failed(userId, name, rawArgs, "Invalid arguments: ${it.message}") }
            val habit = HabitService.findById(args.habitId, userId)
                ?: return failed(userId, name, rawArgs, "Habit ${args.habitId} not found")
            val tz = UserSettingsService.getTimezone(userId) ?: ZoneOffset.UTC
            val nowLocal = ZonedDateTime.now(tz)
            val today = nowLocal.toLocalDate()
            val date = args.date?.let {
                try { LocalDate.parse(it) } catch (_: DateTimeParseException) {
                    return failed(userId, name, rawArgs, "Invalid date — use YYYY-MM-DD")
                }
            } ?: today
            if (date.isAfter(today)) return failed(userId, name, rawArgs, "Cannot check in for a future date ($date > $today in $tz)")

            val comment = args.comment?.trim()?.ifEmpty { null }
            if (comment != null && habit.type != HabitType.QUANTITY) {
                return failed(userId, name, rawArgs, "'comment' is only supported for quantity habits")
            }

            when (habit.type) {
                HabitType.SCHEDULED -> {
                    val reminders = HabitService.listReminders(args.habitId, userId)
                    if (reminders.isEmpty()) return failed(userId, name, rawArgs, "Habit ${args.habitId} has no reminders configured")
                    val requestedTime = args.reminderTime?.let {
                        try { LocalTime.parse(it, TimeFmt) } catch (_: DateTimeParseException) {
                            return failed(userId, name, rawArgs, "Invalid reminderTime — use HH:MM")
                        }
                    }
                    val reminder = when {
                        requestedTime != null -> reminders.firstOrNull { it.time == requestedTime }
                            ?: return failed(userId, name, rawArgs, "No reminder at $requestedTime; available: ${reminders.joinToString { it.time.format(TimeFmt) }}")
                        reminders.size == 1 -> reminders[0]
                        else -> return failed(userId, name, rawArgs, "Habit has ${reminders.size} reminders; specify reminderTime (HH:MM). Available: ${reminders.joinToString { it.time.format(TimeFmt) }}")
                    }
                    if (date == today && nowLocal.toLocalTime() < reminder.time) {
                        return failed(userId, name, rawArgs, "Reminder ${reminder.time.format(TimeFmt)} hasn't fired yet today")
                    }
                    val status = when (val raw = args.status?.lowercase()) {
                        null, "done" -> CheckinStatus.DONE
                        "skip" -> CheckinStatus.SKIP
                        else -> return failed(userId, name, rawArgs, "Invalid status '$raw' — use 'done' or 'skip'")
                    }
                    val recorded = CheckInService.record(reminder.id, userId, date, status)
                    if (!recorded) return failed(userId, name, rawArgs, "Failed to record check-in")
                    KSLog.info("mcp $name user=$userId habit=${args.habitId} reminder=${reminder.id} date=$date status=${status.value}")
                    BotNotifier.notify(userId, Strings.mcpRecordedScheduled(lang, habit.name, reminder.time.format(TimeFmt), status == CheckinStatus.DONE, date))
                    ok("""{"recorded":true,"habitId":${args.habitId},"reminderId":${reminder.id},"date":"$date","status":"${status.value}"}""")
                }
                HabitType.COUNTER -> {
                    val count = (args.value ?: 1.0).toInt()
                    if (count < 1 || count > 100) return failed(userId, name, rawArgs, "'value' must be 1..100 for counter habits")
                    repeat(count) { CheckInService.checkInCounter(args.habitId, userId, date) }
                    KSLog.info("mcp $name user=$userId habit=${args.habitId} date=$date count=$count")
                    BotNotifier.notify(userId, Strings.mcpRecordedCounter(lang, habit.name, count, date))
                    ok("""{"recorded":true,"habitId":${args.habitId},"date":"$date","count":$count}""")
                }
                HabitType.QUANTITY -> {
                    if (habit.isGroupRoot) {
                        return failed(userId, name, rawArgs,
                            "Habit ${args.habitId} is a multi-field group root; call checkin_record per field (see fields[].id from habits_list)")
                    }
                    val value = args.value ?: return failed(userId, name, rawArgs, "'value' is required for quantity habits")
                    if (value <= 0.0 || value.isNaN() || value.isInfinite()) return failed(userId, name, rawArgs, "'value' must be > 0")
                    CheckInService.recordQuantity(args.habitId, userId, date, value, comment)
                    KSLog.info("mcp $name user=$userId habit=${args.habitId} date=$date amount=$value comment=${comment != null}")
                    BotNotifier.notify(userId, Strings.mcpRecordedQuantity(lang, habit.name, value, habit.unit, date, comment))
                    ok("""{"recorded":true,"habitId":${args.habitId},"date":"$date","amount":$value}""")
                }
            }
        } catch (e: Throwable) {
            crashed(userId, name, rawArgs, e)
        }
    }
}
