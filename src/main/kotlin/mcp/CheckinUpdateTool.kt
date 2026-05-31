package mcp

import BotNotifier
import services.CheckInService
import services.HabitService
import Lang
import Strings
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ToolAnnotations
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.*
import java.time.LocalDate
import java.time.ZoneId

object CheckinUpdateTool : McpTool {
    override val name = "checkin_update"
    override val description =
        "Edit a quantity check-in by its checkinId (same id returned by quantity_record or listed in checkins_list). " +
            "Only entries dated today or yesterday can be edited. Editable fields: " +
            "'comment' (string or null to clear — key must be present to change it), " +
            "'values' (array of { paramId, value } — only listed params are updated, others stay). " +
            "At least one of 'comment' or 'values' must be provided."
    override val inputSchema: ToolSchema = buildSchema()
    override val annotations = ToolAnnotations(
        readOnlyHint = false,
        destructiveHint = false,
        idempotentHint = true,
        openWorldHint = false,
    )

    override fun handle(userId: Long, lang: Lang, tz: ZoneId, request: CallToolRequest): CallToolResult {
        val args = request.arguments ?: return err("arguments required")
        val checkinId = args["checkinId"]?.jsonPrimitive?.longOrNull ?: return err("checkinId is required")

        val hasComment = "comment" in args
        val hasValues = "values" in args
        if (!hasComment && !hasValues) return err("Provide at least 'comment' or 'values' to update")

        val commentElem = args["comment"]
        val newComment = if (!hasComment) null
                         else if (commentElem == null || commentElem is JsonNull) null
                         else commentElem.jsonPrimitive.content.trim().ifEmpty { null }

        val valuePatch = mutableMapOf<Long, Double>()
        if (hasValues) {
            val arr = args["values"] as? JsonArray ?: return err("'values' must be an array")
            for ((i, item) in arr.withIndex()) {
                val obj = item as? JsonObject ?: return err("values[$i] must be an object")
                val paramId = obj["paramId"]?.jsonPrimitive?.longOrNull
                    ?: return err("values[$i].paramId is required")
                val value = obj["value"]?.jsonPrimitive?.doubleOrNull
                    ?: return err("values[$i].value is required and must be a number")
                if (value <= 0 || value.isNaN() || value.isInfinite())
                    return err("values[$i].value must be > 0 (paramId $paramId)")
                valuePatch[paramId] = value
            }
            if (valuePatch.isEmpty()) return err("'values' must be non-empty")
        }

        val yesterday = LocalDate.now(tz).minusDays(1)
        val updated = when (val outcome = CheckInService.updateCheckin(
            checkinId, userId, yesterday, hasComment, newComment, valuePatch
        )) {
            is CheckInService.UpdateOutcome.Updated -> outcome.checkin
            CheckInService.UpdateOutcome.NotFound ->
                return err("Check-in $checkinId not found, already deleted, or not a quantity entry")
            is CheckInService.UpdateOutcome.TooOld ->
                return err("Cannot edit check-ins dated earlier than yesterday ($yesterday); ${outcome.date} is too old")
        }

        val habit = HabitService.findById(updated.habitId, userId)
        val lines = buildList {
            updated.values.forEach { v ->
                val param = habit?.params?.firstOrNull { it.id == v.paramId }
                val name = param?.name ?: habit?.name ?: "#${v.paramId}"
                val unitSrc = param?.unit ?: habit?.unit
                val qty = v.quantity
                if (qty != null) {
                    val unit = unitSrc?.let { " $it" } ?: ""
                    add("$name: ${Strings.formatAmount(qty)}$unit")
                }
            }
            if (hasComment) add(
                if (newComment != null) "💬 $newComment"
                else if (lang == Lang.RU) "комментарий удалён" else "comment cleared"
            )
        }
        BotNotifier.notify(userId, Strings.mcpUpdatedCheckin(lang, lines, updated.date))

        return ok("""{"updated":true,"checkinId":$checkinId,"date":"${updated.date}"}""")
    }

    private fun buildSchema(): ToolSchema {
        val props = buildJsonObject {
            putJsonObject("checkinId") {
                put("type", "integer")
                put("description", "The check-in id to edit (from quantity_record or checkins_list).")
            }
            putJsonObject("comment") {
                put("description", "New comment string, or null to clear it. Omit this key entirely to leave the comment unchanged.")
            }
            putJsonObject("values") {
                put("type", "array")
                put("minItems", 1)
                put("description", "Params to update. Only listed paramIds are changed; unlisted params keep their current values.")
                putJsonObject("items") {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("paramId") {
                            put("type", "integer")
                            put("description", "Param id (from habits_list params[].id for this habit).")
                        }
                        putJsonObject("value") {
                            put("type", "number")
                            put("exclusiveMinimum", 0)
                            put("description", "New quantity value; must be > 0.")
                        }
                    }
                    putJsonArray("required") { add("paramId"); add("value") }
                }
            }
        }
        return ToolSchema(properties = props, required = listOf("checkinId"))
    }
}
