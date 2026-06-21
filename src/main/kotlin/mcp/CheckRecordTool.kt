package mcp

import BotNotifier
import services.CheckInService
import services.TrackService
import Lang
import Strings
import dto.CheckRecordArgs
import dto.TrackType
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
        "Log one ad-hoc \"+1\" check-in for a check track (the tap-to-count kind). Only works on a check track with " +
            "allowAdHoc=true (see tracks_list); scheduled-only check tracks are rejected. 'trackId' is the check track. " +
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
        val track = TrackService.findById(args.trackId, userId)
            ?: return err("Track ${args.trackId} not found")
        if (track.type != TrackType.CHECK) return err("Track ${args.trackId} is not a check track")
        if (!track.allowAdHoc) {
            return err("Track ${args.trackId} is scheduled-only (allowAdHoc=false); it can't take ad-hoc check-ins")
        }

        val today = LocalDate.now(tz)
        val date = args.date?.let {
            try { LocalDate.parse(it) } catch (_: DateTimeParseException) {
                return err("Invalid date — use YYYY-MM-DD")
            }
        } ?: today
        if (date.isAfter(today)) return err("Cannot check in for a future date ($date > $today in $tz)")

        val comment = args.comment?.trim()?.ifEmpty { null }
        if (!CheckInService.checkInCounter(args.trackId, userId, date, comment)) {
            return err("Failed to record check-in for track ${args.trackId}")
        }

        val total = CheckInService.counterCountOn(args.trackId, date)
        BotNotifier.notify(userId, Strings.mcpRecordedCheck(lang, track, total, date, comment))
        return ok("""{"recorded":true,"trackId":${args.trackId},"date":"$date","total":$total}""")
    }

    private fun buildSchema(): ToolSchema {
        val props = buildJsonObject {
            putJsonObject("trackId") {
                put("type", "integer")
                put("description", "The check track's id (from tracks_list). Must have allowAdHoc=true.")
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
        return ToolSchema(properties = props, required = listOf("trackId"))
    }
}
