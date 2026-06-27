package mcp

import BotNotifier
import Lang
import Strings
import dto.TrackParamDeleteArgs
import services.TrackService
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ToolAnnotations
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.time.ZoneId

object TrackParamDeleteTool : TypedMcpTool<TrackParamDeleteArgs>(TrackParamDeleteArgs.serializer()) {
    override val name = "track_param_delete"
    override val description =
        "Remove one field (param) from a multi-field track. 'paramId' comes from tracks_list params[].id. " +
            "The field is soft-deleted: its past check-in values are kept for history but it disappears from the " +
            "active track. Refused if it's the track's only remaining field (a track must keep at least one)."
    override val inputSchema: ToolSchema = buildSchema()
    override val annotations = ToolAnnotations(
        readOnlyHint = false,
        destructiveHint = true,
        idempotentHint = false,
        openWorldHint = false,
    )

    override fun handle(userId: Long, lang: Lang, tz: ZoneId, args: TrackParamDeleteArgs): CallToolResult {
        val track = TrackService.listActive(userId).firstOrNull { t -> t.params.any { it.id == args.paramId } }
            ?: return err("Param ${args.paramId} not found among your active tracks")
        val param = track.params.first { it.id == args.paramId }

        if (!TrackService.deleteParam(args.paramId, userId))
            return err("Cannot delete param ${args.paramId}: a track must keep at least one field")

        val label = param.name ?: "#${param.id}"
        BotNotifier.notify(userId, Strings.mcpTrackUpdated(lang, track.name, listOf("field removed: $label")))
        return ok("""{"deleted":true,"paramId":${param.id},"trackId":${track.id}}""")
    }

    private fun buildSchema(): ToolSchema {
        val props = buildJsonObject {
            putJsonObject("paramId") { put("type", "integer"); put("description", "Field id from tracks_list params[].id.") }
        }
        return ToolSchema(properties = props, required = listOf("paramId"))
    }
}
