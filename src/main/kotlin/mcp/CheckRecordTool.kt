package mcp

import BotNotifier
import services.CheckInService
import services.HabitService
import Lang
import Strings
import dto.CheckRecordArgs
import dto.HabitType
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ToolAnnotations
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeParseException

object CheckRecordTool : TypedMcpTool<CheckRecordArgs>(CheckRecordArgs.serializer()) {
    override val name = "check_record"
    override val description =
        "Log one ad-hoc \"+1\" check-in for a check habit (the tap-to-count kind). Only works on a check habit with " +
            "allowAdHoc=true (see habits_list); scheduled-only check habits are rejected. 'habitId' is the check habit. " +
            "Date is optional (YYYY-MM-DD), defaults to today in the user's timezone; future dates are rejected. " +
            "'comment' is optional. Returns the new total count for that date."
    override val inputSchema: ToolSchema = buildSchema()
    override val annotations = ToolAnnotations(
        readOnlyHint = false,
        destructiveHint = false,
        idempotentHint = false,
        openWorldHint = false,
    )

    override fun handle(userId: Long, lang: Lang, tz: ZoneId, args: CheckRecordArgs): CallToolResult {
        val habit = HabitService.findById(args.habitId, userId)
            ?: return err("Habit ${args.habitId} not found")
        if (habit.type != HabitType.CHECK) return err("Habit ${args.habitId} is not a check habit")
        if (!habit.allowAdHoc) {
            return err("Habit ${args.habitId} is scheduled-only (allowAdHoc=false); it can't take ad-hoc check-ins")
        }

        val today = LocalDate.now(tz)
        val date = args.date?.let {
            try { LocalDate.parse(it) } catch (_: DateTimeParseException) {
                return err("Invalid date — use YYYY-MM-DD")
            }
        } ?: today
        if (date.isAfter(today)) return err("Cannot check in for a future date ($date > $today in $tz)")

        val comment = args.comment?.trim()?.ifEmpty { null }
        if (!CheckInService.checkInCounter(args.habitId, userId, date, comment)) {
            return err("Failed to record check-in for habit ${args.habitId}")
        }

        val total = CheckInService.counterCountOn(args.habitId, date)
        BotNotifier.notify(userId, Strings.mcpRecordedCheck(lang, habit, total, date, comment))
        return ok("""{"recorded":true,"habitId":${args.habitId},"date":"$date","total":$total}""")
    }

    private fun buildSchema(): ToolSchema {
        val props = buildJsonObject {
            putJsonObject("habitId") {
                put("type", "integer")
                put("description", "The check habit's id (from habits_list). Must have allowAdHoc=true.")
            }
            putJsonObject("date") {
                put("type", "string")
                put("format", "date")
                put("pattern", "^\\d{4}-\\d{2}-\\d{2}$")
                put("description", "Check-in date (YYYY-MM-DD). Default: today in the user's timezone. Future dates rejected.")
            }
            putJsonObject("comment") {
                put("type", "string")
                put("description", "Optional note attached to the event.")
            }
        }
        return ToolSchema(properties = props, required = listOf("habitId"))
    }
}
