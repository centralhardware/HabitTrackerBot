package mcp

import BotNotifier
import HabitService
import Lang
import Strings
import dto.Direction
import dto.HabitCreateArgs
import dto.HabitType
import dto.McpJson
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

private val TimeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

object HabitCreateTool : TypedMcpTool<HabitCreateArgs>(HabitCreateArgs.serializer()) {
    override val name = "habit_create"
    override val description =
        "Create a single habit. type: 'scheduled' (reminders required, each reminder is a daily check-in slot), 'counter' (tally events; optional dailyTarget and direction), or 'quantity' (log an amount; optional dailyTarget, unit, direction). reminders is an array of HH:MM strings; required for scheduled, optional otherwise. dailyTarget must be > 0. direction is 'more' or 'less'. For multi-field quantity habits use 'habit_create_group' instead."
    override val inputSchema: ToolSchema = buildSchema()

    override fun handle(userId: Long, lang: Lang, tz: ZoneId, args: HabitCreateArgs): CallToolResult {
        val name = args.name.trim()
        if (name.isBlank()) return err("'name' must not be blank")

        val type = HabitType.entries.firstOrNull { it.value == args.type.lowercase() }
            ?: return err("Invalid type '${args.type}' — use 'scheduled', 'counter' or 'quantity'")

        val reminders = parseTimes(args.reminders) ?: return err("Invalid reminders — use HH:MM")
        if (type == HabitType.SCHEDULED && reminders.isEmpty()) {
            return err("Scheduled habits require at least one reminder (HH:MM)")
        }

        val dailyTarget = args.dailyTarget
        if (dailyTarget != null && (dailyTarget <= 0.0 || dailyTarget.isNaN() || dailyTarget.isInfinite())) {
            return err("'dailyTarget' must be > 0")
        }

        val direction = args.direction?.let {
            Direction.parse(it.lowercase()) ?: return err("Invalid direction '$it' — use 'more' or 'less'")
        }
        val unit = args.unit?.trim()?.ifEmpty { null }?.take(16)

        val habit = HabitService.addHabit(
            userId = userId,
            name = name.take(64),
            type = type,
            reminders = reminders,
            dailyTarget = dailyTarget,
            unit = unit,
            direction = direction,
        )
        BotNotifier.notify(userId, Strings.mcpCreatedHabit(lang, habit.name))
        return ok(McpJson.encodeToString(habit))
    }

    private fun buildSchema(): ToolSchema {
        val props = buildJsonObject {
            putJsonObject("name") { put("type", "string") }
            putJsonObject("type") {
                put("type", "string")
                putJsonArray("enum") { add("scheduled"); add("counter"); add("quantity") }
            }
            putJsonObject("reminders") {
                put("type", "array")
                putJsonObject("items") {
                    put("type", "string")
                    put("pattern", "^[0-2][0-9]:[0-5][0-9]$")
                }
            }
            putJsonObject("dailyTarget") {
                put("type", "number")
                put("exclusiveMinimum", 0)
            }
            putJsonObject("unit") { put("type", "string") }
            putJsonObject("direction") {
                put("type", "string")
                putJsonArray("enum") { add("more"); add("less") }
            }
        }
        return ToolSchema(properties = props, required = listOf("name", "type"))
    }
}

internal fun parseTimes(raw: List<String>): List<LocalTime>? = try {
    raw.map { LocalTime.parse(it.trim(), TimeFmt) }.distinct().sorted()
} catch (_: DateTimeParseException) {
    null
}
