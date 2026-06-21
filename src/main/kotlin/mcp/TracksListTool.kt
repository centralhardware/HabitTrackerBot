package mcp

import services.TrackService
import Lang
import dto.McpJson
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ToolAnnotations
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import java.time.ZoneId

object TracksListTool : McpTool {
    override val name = "tracks_list"
    override val description =
        "List the authenticated user's active tracks. Call this first — every other tool takes ids returned here. " +
            "Each track has: id (use as trackId), name, type (check = done/skip track, quantity = numeric amount, " +
            "timer = tracked time), status, and params[] (the trackable fields). A check track marks reminder slots when " +
            "it has reminders and/or accepts ad-hoc \"+1\" check-ins when allowAdHoc=true (use check_record for those). " +
            "A single-field track has one param and hoists its unit/dailyTarget/direction onto the track row itself, so " +
            "you can read them there; a multi-field quantity track (multiField=true) exposes several params[], each with " +
            "its own id, name and unit — use params[].id as the paramId for quantity_record and to read checkins_list " +
            "rows. Optional per-track fields (dailyTarget, unit, direction, logOnly, allowAdHoc) are omitted when unset."
    override val inputSchema: ToolSchema = emptyObjectSchema()
    override val annotations = ToolAnnotations(readOnlyHint = true, openWorldHint = false)

    override fun handle(userId: Long, lang: Lang, tz: ZoneId, request: CallToolRequest): CallToolResult {
        val tracks = TrackService.listActive(userId)
        return ok(McpJson.encodeToString(tracks))
    }
}
