package mcp

import services.CheckInService
import services.TrackService
import services.ParamValueService
import Lang
import dto.CheckinsListArgs
import dto.CheckinsListResult
import dto.TrackCheckins
import dto.McpJson
import dto.ParamDictionary
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ToolAnnotations
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

object CheckinsListTool : TypedMcpTool<CheckinsListArgs>(CheckinsListArgs.serializer()) {
    override val name = "checkins_list"
    override val description =
        "List past check-ins for one or more tracks between two dates (inclusive). 'trackIds' is an array of track ids " +
            "(batch query); omit it or pass an empty array to fetch all of the user's active tracks. The response is " +
            "{ from, to, tracks[] } where from/to echo the resolved window and tracks[] " +
            "has one track per id. Defaults: from = today - 30 days, to = today (in the user's " +
            "timezone). Maximum range 366 days. Each check-in row has checkinId (pass it to checkin_delete to remove the " +
            "track), paramId (which field of a multi-field track it belongs to; see tracks_list params; omitted for counter events), date, status " +
            "(done/skip/pending for scheduled tracks), quantity (for quantity tracks), reminderTime (for scheduled tracks), " +
            "recordedAt (ISO-8601 instant the track was written; for a timer this is when it was stopped, i.e. the session end), " +
            "startedAt (ISO-8601 instant; only on a timer's duration row — the session start, derived as recordedAt minus the elapsed seconds), and " +
            "comment (when set). Unknown track ids are returned with found=false. Each track track also carries " +
            "paramValues[] — the dictionary of recurring (seen more than once) param values ({ paramId, values:[{ value, uses }] }), " +
            "most-used first; use it with param_values_merge to fold near-duplicate values together (omitted when the " +
            "track has no recurring values yet)."
    override val inputSchema: ToolSchema = buildSchema()
    override val annotations = ToolAnnotations(readOnlyHint = true, openWorldHint = false)

    override fun handle(userId: Long, lang: Lang, tz: ZoneId, args: CheckinsListArgs): CallToolResult {
        val today = LocalDate.now(tz)
        val to = args.to?.let {
            try { LocalDate.parse(it) } catch (_: DateTimeParseException) {
                return err("Invalid 'to' — use YYYY-MM-DD")
            }
        } ?: today
        val from = args.from?.let {
            try { LocalDate.parse(it) } catch (_: DateTimeParseException) {
                return err("Invalid 'from' — use YYYY-MM-DD")
            }
        } ?: to.minusDays(30)
        if (from.isAfter(to)) return err("'from' must be on or before 'to'")
        if (ChronoUnit.DAYS.between(from, to) > 366) {
            return err("Range too large; max 366 days")
        }
        val trackIds = args.trackIds.ifEmpty { TrackService.listActive(userId).map { it.id } }
        val tracks = trackIds.distinct().map { trackId ->
            val rows = CheckInService.listInRange(trackId, userId, from, to)
            val paramValues = TrackService.findById(trackId, userId)?.params
                ?.map { ParamDictionary(it.id, ParamValueService.listValues(it.id)) }
                ?.filter { it.values.isNotEmpty() }
                ?: emptyList()
            TrackCheckins(trackId, found = rows != null, checkins = rows ?: emptyList(), paramValues = paramValues)
        }
        val result = CheckinsListResult(from = from.toString(), to = to.toString(), tracks = tracks)
        return ok(McpJson.encodeToString(result))
    }

    private fun buildSchema(): ToolSchema {
        val props = buildJsonObject {
            putJsonObject("trackIds") {
                put("type", "array")
                put("description", "Track ids to fetch (from tracks_list); one result track per id. Omit or leave empty to fetch all active tracks.")
                putJsonObject("items") { put("type", "integer") }
            }
            putJsonObject("from") {
                put("type", "string")
                put("format", "date")
                put("pattern", "^\\d{4}-\\d{2}-\\d{2}$")
                put("description", "Start date, inclusive (YYYY-MM-DD). Default: 'to' minus 30 days.")
            }
            putJsonObject("to") {
                put("type", "string")
                put("format", "date")
                put("pattern", "^\\d{4}-\\d{2}-\\d{2}$")
                put("description", "End date, inclusive (YYYY-MM-DD). Default: today in the user's timezone.")
            }
        }
        return ToolSchema(properties = props, required = emptyList())
    }
}
