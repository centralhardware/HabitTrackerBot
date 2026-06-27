package mcp

import BotNotifier
import Lang
import Strings
import dto.TrackLifecycleArgs
import dto.TrackStatus
import services.TrackService
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ToolAnnotations
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.time.ZoneId

object TrackLifecycleTool : TypedMcpTool<TrackLifecycleArgs>(TrackLifecycleArgs.serializer()) {
    override val name = "track_lifecycle"
    override val description =
        "Change a track's lifecycle. 'trackId' comes from tracks_list. 'action': " +
            "\"pause\" (stop reminders; 'pauseDays' > 0 auto-resumes after that many days, 0 = until resumed), " +
            "\"resume\" (un-pause), or \"delete\" (soft-delete — hidden from active tracks, history kept, not reversible " +
            "via this API). pause only works on an active track, resume only on a paused one."
    override val inputSchema: ToolSchema = buildSchema()
    override val annotations = ToolAnnotations(
        readOnlyHint = false,
        destructiveHint = true,
        idempotentHint = false,
        openWorldHint = false,
    )

    override fun handle(userId: Long, lang: Lang, tz: ZoneId, args: TrackLifecycleArgs): CallToolResult {
        val track = TrackService.findById(args.trackId, userId)
            ?: return err("Track ${args.trackId} not found")

        return when (args.action.trim().lowercase()) {
            "pause" -> {
                if (track.status == TrackStatus.PAUSED) return err("Track ${track.id} is already paused")
                val days = args.pauseDays.coerceAtLeast(0)
                if (!TrackService.pause(track.id, userId, days))
                    return err("Track ${track.id} could not be paused (must be active)")
                BotNotifier.notify(userId, Strings.mcpTrackPaused(lang, track.name, days))
                ok("""{"trackId":${track.id},"status":"paused"}""")
            }
            "resume" -> {
                if (track.status != TrackStatus.PAUSED) return err("Track ${track.id} is not paused")
                if (!TrackService.resume(track.id, userId))
                    return err("Track ${track.id} could not be resumed")
                BotNotifier.notify(userId, Strings.mcpTrackResumed(lang, track.name))
                ok("""{"trackId":${track.id},"status":"active"}""")
            }
            "delete" -> {
                if (!TrackService.softDelete(track.id, userId))
                    return err("Track ${track.id} could not be deleted")
                BotNotifier.notify(userId, Strings.mcpTrackDeleted(lang, track.name))
                ok("""{"trackId":${track.id},"status":"deleted"}""")
            }
            else -> err("'action' must be one of: pause, resume, delete")
        }
    }

    private fun buildSchema(): ToolSchema {
        val props = buildJsonObject {
            putJsonObject("trackId") { put("type", "integer"); put("description", "The track's id (from tracks_list).") }
            putJsonObject("action") {
                put("type", "string")
                putJsonArray("enum") { add("pause"); add("resume"); add("delete") }
                put("description", "pause, resume, or delete.")
            }
            putJsonObject("pauseDays") {
                put("type", "integer")
                put("minimum", 0)
                put("description", "For pause: days until auto-resume; 0 (default) = indefinite.")
            }
        }
        return ToolSchema(properties = props, required = listOf("trackId", "action"))
    }
}
