package mcp

import BotNotifier
import services.CheckInService
import services.HabitService
import Lang
import Strings
import dto.CheckinDeleteArgs
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ToolAnnotations
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.time.LocalDate
import java.time.ZoneId

object CheckinDeleteTool : TypedMcpTool<CheckinDeleteArgs>(CheckinDeleteArgs.serializer()) {
    override val name = "checkin_delete"
    override val description =
        "Soft-delete a previously logged quantity check-in by its 'checkinId' (the id each row carries in " +
            "checkins_list). Removes the whole event at once — a multi-field quantity entry has all its values dropped " +
            "together. Only quantity entries can be deleted (not counter or scheduled reminder check-ins), and only " +
            "those dated today or yesterday — older entries cannot be deleted. Use this to retract a mistake, e.g. " +
            "a quantity logged just after midnight that belonged to the previous day: delete it, then re-record with " +
            "quantity_record passing the correct 'date'."
    override val inputSchema: ToolSchema = buildSchema()
    override val annotations = ToolAnnotations(
        readOnlyHint = false,
        destructiveHint = true,
        idempotentHint = true,
        openWorldHint = false,
    )

    override fun handle(userId: Long, lang: Lang, tz: ZoneId, args: CheckinDeleteArgs): CallToolResult {
        val yesterday = LocalDate.now(tz).minusDays(1)
        val deleted = when (val outcome = CheckInService.deleteCheckin(args.checkinId, userId, yesterday)) {
            is CheckInService.DeleteOutcome.Deleted -> outcome.checkin
            CheckInService.DeleteOutcome.NotFound ->
                return err("Check-in ${args.checkinId} not found, already deleted, or not a quantity entry")
            is CheckInService.DeleteOutcome.TooOld ->
                return err("Cannot delete check-ins dated earlier than yesterday ($yesterday); ${outcome.date} is too old")
        }

        val habit = HabitService.findById(deleted.habitId, userId)
        val lines = deleted.values.map { v ->
            val param = habit?.params?.firstOrNull { it.id == v.paramId }
            val name = param?.name ?: habit?.name ?: "#${v.paramId}"
            val unitSrc = param?.unit ?: habit?.unit
            val detail = when {
                v.quantity != null -> {
                    val unit = unitSrc?.let { " $it" } ?: ""
                    "${Strings.formatAmount(v.quantity)}$unit"
                }
                v.status != null -> v.status.value
                else -> "—"
            }
            "$name: $detail"
        }
        BotNotifier.notify(userId, Strings.mcpDeletedCheckin(lang, lines, deleted.date))

        return ok("""{"deleted":true,"checkinId":${deleted.checkinId},"date":"${deleted.date}","values":${deleted.values.size}}""")
    }

    private fun buildSchema(): ToolSchema {
        val props = buildJsonObject {
            putJsonObject("checkinId") {
                put("type", "integer")
                put("description", "checkinId of the entry to delete — the value each row carries in checkins_list (or returned by quantity_record).")
            }
        }
        return ToolSchema(properties = props, required = listOf("checkinId"))
    }
}
