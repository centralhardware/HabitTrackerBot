package mcp

import BotNotifier
import services.CheckInService
import services.TrackService
import Lang
import Strings
import dto.CheckinDeleteArgs
import dto.FieldValue
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ToolAnnotations
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.time.ZoneId

object CheckinDeleteTool : TypedMcpTool<CheckinDeleteArgs>(CheckinDeleteArgs.serializer()) {
    override val name = "checkin_delete"
    override val description =
        "Soft-delete a previously logged quantity check-in by its 'checkinId' (the id each row carries in " +
            "checkins_list). Removes the whole event at once — a multi-field quantity track has all its values dropped " +
            "together. Only quantity tracks can be deleted (not counter or scheduled reminder check-ins), and only " +
            "those dated within the last month — older tracks cannot be deleted. Use this to retract a mistake, e.g. " +
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
        // Window is "within the last month": today back through one month ago inclusive.
        val cutoff = checkinEditCutoff(tz)
        val deleted = when (val outcome = CheckInService.deleteCheckin(args.checkinId, userId, cutoff)) {
            is CheckInService.DeleteOutcome.Deleted -> outcome.checkin
            CheckInService.DeleteOutcome.NotFound ->
                return err("Check-in ${args.checkinId} not found, already deleted, or not a quantity track")
            is CheckInService.DeleteOutcome.TooOld ->
                return err("Cannot delete check-ins older than a $CHECKIN_EDIT_WINDOW ($cutoff); ${outcome.date} is too old")
        }

        val track = TrackService.findById(deleted.trackId, userId)
        val lines = deleted.values.map { v ->
            val param = track?.params?.firstOrNull { it.id == v.paramId }
            val name = param?.name ?: track?.name ?: "#${v.paramId}"
            val detail = when (val fv = v.value) {
                is FieldValue.Numeric -> Strings.paramAmount(lang, track, param, fv.v)
                is FieldValue.Text -> fv.v
                null -> v.status?.value ?: "—"
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
                put("description", "checkinId of the track to delete — the value each row carries in checkins_list (or returned by quantity_record).")
            }
        }
        return ToolSchema(properties = props, required = listOf("checkinId"))
    }
}
