package mcp

import BotNotifier
import services.CheckInService
import services.HabitService
import Lang
import Strings
import dto.CheckinDeleteArgs
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.time.ZoneId

object CheckinDeleteTool : TypedMcpTool<CheckinDeleteArgs>(CheckinDeleteArgs.serializer()) {
    override val name = "checkin_delete"
    override val description =
        "Soft-delete a previously logged check-in by its 'checkinId' (the id each row carries in checkins_list). " +
            "Removes the whole event at once — a multi-field quantity entry has all its values dropped together. " +
            "Only manual entries (counter/quantity) can be deleted, not scheduled reminder check-ins. Use this to " +
            "retract a mistake, e.g. a quantity logged just after midnight that belonged to the previous day: delete " +
            "it, then re-record with quantity_record passing the correct 'date'."
    override val inputSchema: ToolSchema = buildSchema()

    override fun handle(userId: Long, lang: Lang, tz: ZoneId, args: CheckinDeleteArgs): CallToolResult {
        val deleted = CheckInService.deleteCheckin(args.checkinId, userId)
            ?: return err("Check-in ${args.checkinId} not found, already deleted, or not a manual entry")

        val lines = deleted.values.map { v ->
            val habit = HabitService.findById(v.habitId, userId)
            val name = habit?.name ?: "#${v.habitId}"
            val detail = when {
                v.quantity != null -> {
                    val unit = habit?.unit?.let { " $it" } ?: ""
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
            putJsonObject("checkinId") { put("type", "integer") }
        }
        return ToolSchema(properties = props, required = listOf("checkinId"))
    }
}
