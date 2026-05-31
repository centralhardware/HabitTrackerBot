package mcp

import BotNotifier
import services.CheckInService
import services.HabitService
import Lang
import Strings
import dto.FieldValue
import dto.HabitType
import dto.ParamType
import dto.QuantityRecordArgs
import dto.parse
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ToolAnnotations
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
            "entry. 'habitId' is the quantity habit. 'values' is an array of { paramId, value } where 'value' is " +
            "always a string — pass a number string (e.g. \"5.5\") for 'number' params, or any text for 'text' params; " +
            "check paramType in habits_list params[]. A single-field habit has one param; a multi-field one has several. " +
            "Date is optional (YYYY-MM-DD), defaults to today in the user's timezone; future dates are rejected. " +
            "'comment' is optional and applies to the whole event. Returns the new checkinId."
    override val inputSchema: ToolSchema = buildSchema()
    override val annotations = ToolAnnotations(
        readOnlyHint = false,
        destructiveHint = false,
        idempotentHint = false,
        openWorldHint = false,
    )

    override fun handle(userId: Long, lang: Lang, tz: ZoneId, args: QuantityRecordArgs): CallToolResult {
        val habit = HabitService.findById(args.habitId, userId)
            ?: return err("Habit ${args.habitId} not found")
        if (habit.type != HabitType.QUANTITY) return err("Habit ${args.habitId} is not a quantity habit")
        if (args.values.isEmpty()) return err("'values' must be non-empty")

        val today = LocalDate.now(tz)
        val date = args.date?.let {
            try { LocalDate.parse(it) } catch (_: DateTimeParseException) {
                return err("Invalid date — use YYYY-MM-DD")
            }
        } ?: today
        if (date.isAfter(today)) return err("Cannot check in for a future date ($date > $today in $tz)")

        val paramById = habit.params.associateBy { it.id }
        val unknown = args.values.map { it.paramId }.filter { it !in paramById }
        if (unknown.isNotEmpty()) {
            return err("Unknown paramId(s) ${unknown.joinToString()}; allowed: ${paramById.keys.joinToString()}")
        }

        val parsed = mutableMapOf<Long, FieldValue>()
        for (fv in args.values) {
            val paramType = paramById[fv.paramId]?.paramType ?: ParamType.NUMBER
            parsed[fv.paramId] = fv.parse(paramType)
                ?: return err(
                    if (paramType == ParamType.TEXT) "values[paramId=${fv.paramId}].value must be non-blank text"
                    else "values[paramId=${fv.paramId}].value must be a number > 0"
                )
        }
        parsed.values.filterIsInstance<FieldValue.Numeric>().firstOrNull { it.v <= 0 || it.v.isNaN() || it.v.isInfinite() }
            ?.let { return err("Numeric values must be > 0") }

        val numericMap = parsed.filterValues { it is FieldValue.Numeric }.mapValues { (it.value as FieldValue.Numeric).v }
        val textMap = parsed.filterValues { it is FieldValue.Text }.mapValues { (it.value as FieldValue.Text).v }
        val comment = args.comment?.trim()?.ifEmpty { null }
        val checkinId = CheckInService.recordQuantity(args.habitId, userId, date, numericMap, textMap, comment)
        if (checkinId <= 0) return err("Failed to record check-in for habit ${args.habitId}")

        val note = when {
            habit.multiField -> Strings.mcpRecordedQuantityGroup(lang, habit, numericMap, textMap, date, comment)
            numericMap.isNotEmpty() -> Strings.mcpRecordedQuantity(lang, habit.name, numericMap.values.first(), habit.unit, date, comment)
            else -> Strings.mcpRecordedQuantityText(lang, habit.name, textMap.values.firstOrNull() ?: "", date, comment)
        }
        BotNotifier.notify(userId, note)
        return ok("""{"recorded":true,"checkinId":$checkinId,"habitId":${args.habitId},"date":"$date","values":${parsed.size}}""")
    }

    private fun buildSchema(): ToolSchema {
        val props = buildJsonObject {
            putJsonObject("habitId") {
                put("type", "integer")
                put("description", "The quantity habit's id (from habits_list).")
            }
            putJsonObject("values") {
                put("type", "array")
                put("minItems", 1)
                put("description", "One { paramId, value } per field. 'value' is always a string: a number string (e.g. \"5.5\") for 'number' params, any text for 'text' params.")
                putJsonObject("items") {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("paramId") {
                            put("type", "integer")
                            put("description", "A param id from habits_list params[].id of this habit.")
                        }
                        putJsonObject("value") {
                            put("type", "string")
                            put("description", "Number string (e.g. \"5.5\") for 'number' params; any text for 'text' params.")
                        }
                    }
                    putJsonArray("required") { add("paramId"); add("value") }
                }
            }
            putJsonObject("date") {
                put("type", "string")
                put("format", "date")
                put("pattern", "^\\d{4}-\\d{2}-\\d{2}$")
                put("description", "Check-in date (YYYY-MM-DD). Default: today in the user's timezone. Future dates rejected.")
            }
            putJsonObject("comment") {
                put("type", "string")
                put("description", "Optional note attached to the whole event.")
            }
        }
        return ToolSchema(properties = props, required = listOf("habitId", "values"))
    }
}
