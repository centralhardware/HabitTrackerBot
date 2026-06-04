package mcp

import BotNotifier
import db.CheckInRepository
import services.CheckInService
import services.HabitService
import Lang
import Strings
import dto.CheckinUpdateArgs
import dto.FieldValue
import dto.ParamType
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

object CheckinUpdateTool : TypedMcpTool<CheckinUpdateArgs>(CheckinUpdateArgs.serializer()) {
    override val name = "checkin_update"
    override val description =
        "Edit a quantity check-in by its checkinId (same id returned by quantity_record or listed in checkins_list). " +
            "Only entries dated within the last 7 days can be edited. Editable fields: " +
            "'comment' (string or omit to leave unchanged), 'clearComment' (true to remove the comment), " +
            "'values' (array of { paramId, value } — same string format as quantity_record; only listed params are updated). " +
            "At least one of 'comment'/'clearComment' or 'values' must be provided."
    override val inputSchema: ToolSchema = buildSchema()
    override val annotations = ToolAnnotations(
        readOnlyHint = false,
        destructiveHint = false,
        idempotentHint = true,
        openWorldHint = false,
    )

    override fun handle(userId: Long, lang: Lang, tz: ZoneId, args: CheckinUpdateArgs): CallToolResult {
        val hasComment = args.clearComment || args.comment != null
        if (!hasComment && args.values.isEmpty()) {
            return err("Provide at least 'comment'/'clearComment' or 'values' to update")
        }
        val newComment = if (args.clearComment) null else args.comment?.trim()?.ifEmpty { null }

        val valuePatch = mutableMapOf<Long, String>()
        if (args.values.isNotEmpty()) {
            val checkin = CheckInRepository.loadEventForDelete(args.checkinId, userId)
                ?: return err("Check-in ${args.checkinId} not found, already deleted, or not editable")
            val paramById = HabitService.findById(checkin.habitId, userId)?.params?.associateBy { it.id } ?: emptyMap()
            for ((i, fv) in args.values.withIndex()) {
                val paramType = paramById[fv.paramId]?.paramType ?: ParamType.NUMBER
                when (val parsed = fv.parse(paramType)) {
                    is FieldValue.Numeric -> {
                        if (parsed.v < 0 || parsed.v.isNaN() || parsed.v.isInfinite())
                            return err("values[$i].value must be ≥ 0 and finite for number param ${fv.paramId}")
                        valuePatch[fv.paramId] = parsed.v.toString()
                    }
                    is FieldValue.Text -> valuePatch[fv.paramId] = parsed.v
                    null -> return err(
                        if (paramType == ParamType.TEXT) "values[$i].value must be non-blank text for param ${fv.paramId}"
                        else "values[$i].value must be a number for param ${fv.paramId}"
                    )
                }
            }
        }

        // Window is "within the last 7 days": today back through today-7 inclusive.
        val weekAgo = LocalDate.now(tz).minusDays(7)
        val updated = when (val outcome = CheckInService.updateCheckin(
            args.checkinId, userId, weekAgo, hasComment, newComment, valuePatch
        )) {
            is CheckInService.UpdateOutcome.Updated -> outcome.checkin
            CheckInService.UpdateOutcome.NotFound ->
                return err("Check-in ${args.checkinId} not found, already deleted, or not a quantity entry")
            is CheckInService.UpdateOutcome.TooOld ->
                return err("Cannot edit check-ins older than 7 days ($weekAgo); ${outcome.date} is too old")
        }

        val habit = HabitService.findById(updated.habitId, userId)
        val lines = buildList {
            updated.values.forEach { v ->
                val param = habit?.params?.firstOrNull { it.id == v.paramId }
                val name = param?.name ?: habit?.name ?: "#${v.paramId}"
                when (val fv = v.value) {
                    is FieldValue.Numeric -> {
                        val unit = (param?.unit ?: habit?.unit)?.let { " $it" } ?: ""
                        add("$name: ${Strings.formatAmount(fv.v)}$unit")
                    }
                    is FieldValue.Text -> add("$name: ${fv.v}")
                    null -> {}
                }
            }
            if (hasComment) add(
                if (newComment != null) "💬 $newComment"
                else if (lang == Lang.RU) "комментарий удалён" else "comment cleared"
            )
        }
        BotNotifier.notify(userId, Strings.mcpUpdatedCheckin(lang, lines, updated.date))
        return ok("""{"updated":true,"checkinId":${args.checkinId},"date":"${updated.date}"}""")
    }

    private fun buildSchema(): ToolSchema {
        val props = buildJsonObject {
            putJsonObject("checkinId") {
                put("type", "integer")
                put("description", "The check-in id to edit (from quantity_record or checkins_list).")
            }
            putJsonObject("comment") {
                put("type", "string")
                put("description", "New comment. Omit to leave unchanged. Use clearComment=true to remove.")
            }
            putJsonObject("clearComment") {
                put("type", "boolean")
                put("description", "Set true to clear the comment. Default false.")
            }
            putJsonObject("values") {
                put("type", "array")
                put("minItems", 1)
                put("description", "Params to update. Same { paramId, value } string format as quantity_record. Unlisted params keep current values.")
                putJsonObject("items") {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("paramId") {
                            put("type", "integer")
                            put("description", "Param id (from habits_list params[].id).")
                        }
                        putJsonObject("value") {
                            put("type", "string")
                            put("description", "Number string for 'number' params; any text for 'text' params.")
                        }
                    }
                    putJsonArray("required") { add("paramId"); add("value") }
                }
            }
        }
        return ToolSchema(properties = props, required = listOf("checkinId"))
    }
}
