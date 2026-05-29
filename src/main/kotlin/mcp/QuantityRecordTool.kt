package mcp

import BotNotifier
import CheckInService
import HabitService
import Lang
import Strings
import dto.HabitType
import dto.QuantityRecordArgs
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeParseException

object QuantityRecordTool : TypedMcpTool<QuantityRecordArgs>(QuantityRecordArgs.serializer()) {
    override val name = "quantity_record"
    override val description =
        "Record a quantity check-in in one call: writes one event row with a shared comment and one value row per " +
            "entry. 'habitId' is the quantity habit. 'values' is an array of { fieldId, value } with value > 0. For a " +
            "multi-field group root (habits_list returns isGroupRoot/fields) each fieldId is one of root.fields[].id. " +
            "A single quantity habit is just a group with one value — pass one entry whose fieldId is the habit's own " +
            "id. Date is optional (YYYY-MM-DD), defaults to today in the user's timezone; future dates are rejected. " +
            "'comment' is optional and applies to the whole event."
    override val inputSchema: ToolSchema = buildSchema()

    override fun handle(userId: Long, lang: Lang, tz: ZoneId, args: QuantityRecordArgs): CallToolResult {
        val habit = HabitService.findById(args.habitId, userId)
            ?: return err("Habit ${args.habitId} not found")
        if (habit.type != HabitType.QUANTITY) {
            return err("Habit ${args.habitId} is not a quantity habit")
        }
        if (args.values.isEmpty()) {
            return err("'values' must be non-empty")
        }
        val today = LocalDate.now(tz)
        val date = args.date?.let {
            try { LocalDate.parse(it) } catch (_: DateTimeParseException) {
                return err("Invalid date — use YYYY-MM-DD")
            }
        } ?: today
        if (date.isAfter(today)) {
            return err("Cannot check in for a future date ($date > $today in $tz)")
        }

        // A single quantity habit logs against its own id; a group root against its field ids.
        val allowedIds = if (habit.isGroupRoot) habit.fields.map { it.id }.toSet() else setOf(habit.id)
        val unknown = args.values.map { it.fieldId }.filter { it !in allowedIds }
        if (unknown.isNotEmpty()) {
            return err("Unknown fieldId(s) ${unknown.joinToString()}; allowed: ${allowedIds.joinToString()}")
        }
        args.values.firstOrNull { it.value <= 0.0 || it.value.isNaN() || it.value.isInfinite() }?.let {
            return err("All 'value' entries must be > 0 (fieldId ${it.fieldId})")
        }

        val map = args.values.associate { it.fieldId to it.value }
        val comment = args.comment?.trim()?.ifEmpty { null }
        val wrote = CheckInService.recordQuantity(args.habitId, userId, date, map, comment)
        if (wrote > 0) {
            val note = if (habit.isGroupRoot) Strings.mcpRecordedQuantityGroup(lang, habit, map, date, comment)
            else Strings.mcpRecordedQuantity(lang, habit.name, map.values.first(), habit.unit, date, comment)
            BotNotifier.notify(userId, note)
        }
        return ok("""{"recorded":true,"habitId":${args.habitId},"date":"$date","wrote":$wrote}""")
    }

    private fun buildSchema(): ToolSchema {
        val props = buildJsonObject {
            putJsonObject("habitId") { put("type", "integer") }
            putJsonObject("values") {
                put("type", "array")
                put("minItems", 1)
                putJsonObject("items") {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("fieldId") { put("type", "integer") }
                        putJsonObject("value") {
                            put("type", "number")
                            put("exclusiveMinimum", 0)
                        }
                    }
                    putJsonArray("required") {
                        add("fieldId"); add("value")
                    }
                }
            }
            putJsonObject("date") {
                put("type", "string")
                put("pattern", "^\\d{4}-\\d{2}-\\d{2}$")
            }
            putJsonObject("comment") { put("type", "string") }
        }
        return ToolSchema(properties = props, required = listOf("habitId", "values"))
    }
}
