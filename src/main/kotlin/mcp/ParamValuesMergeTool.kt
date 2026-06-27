package mcp

import Lang
import dto.ParamValuesMergeArgs
import services.TrackService
import services.ParamValueService
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ToolAnnotations
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.time.ZoneId

object ParamValuesMergeTool : TypedMcpTool<ParamValuesMergeArgs>(ParamValuesMergeArgs.serializer()) {
    override val name = "param_values_merge"
    override val description =
        "Fold near-duplicate values of a param into one canonical value — e.g. merge " +
            "[\"brew method\",\"Brew method\"] into \"V60\", or fix a typo. Every check-in using a 'from' value is " +
            "repointed to 'into', and the emptied 'from' tracks are dropped from the dictionary. 'into' may be an " +
            "existing value or a brand-new label (it's created if needed). 'trackId'/'paramId' come from tracks_list. " +
            "Only values that have recurred have a dictionary track; read the exact strings from tracks_list " +
            "params[].recurringValues first. Returns the number of check-ins repointed."
    override val inputSchema: ToolSchema = buildSchema()
    override val annotations = ToolAnnotations(
        readOnlyHint = false,
        destructiveHint = true,
        idempotentHint = true,
        openWorldHint = false,
    )

    override fun handle(userId: Long, lang: Lang, tz: ZoneId, args: ParamValuesMergeArgs): CallToolResult {
        val track = TrackService.findById(args.trackId, userId)
            ?: return err("Track ${args.trackId} not found")
        track.params.firstOrNull { it.id == args.paramId }
            ?: return err("Param ${args.paramId} is not a field of track ${args.trackId}")

        val into = args.into.trim()
        if (into.isEmpty()) return err("'into' must be non-blank")
        val from = args.from.map { it.trim() }.filter { it.isNotEmpty() && it != into }.distinct()
        if (from.isEmpty()) return err("'from' must list at least one value other than 'into'")

        val repointed = ParamValueService.mergeValues(args.paramId, from, into)
        return ok("""{"merged":true,"into":${jsonStr(into)},"from":${from.size},"repointed":$repointed}""")
    }

    private fun jsonStr(s: String): String =
        buildString {
            append('"')
            s.forEach { c ->
                when (c) {
                    '"' -> append("\\\"")
                    '\\' -> append("\\\\")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(c)
                }
            }
            append('"')
        }

    private fun buildSchema(): ToolSchema {
        val props = buildJsonObject {
            putJsonObject("trackId") {
                put("type", "integer")
                put("description", "The track's id (from tracks_list).")
            }
            putJsonObject("paramId") {
                put("type", "integer")
                put("description", "A param id from tracks_list params[].id of this track.")
            }
            putJsonObject("from") {
                put("type", "array")
                put("minItems", 1)
                put("description", "The values to fold away (exact strings from tracks_list params[].recurringValues).")
                putJsonObject("items") { put("type", "string") }
            }
            putJsonObject("into") {
                put("type", "string")
                put("description", "The canonical value to keep. May be an existing value or a new label.")
            }
        }
        return ToolSchema(properties = props, required = listOf("trackId", "paramId", "from", "into"))
    }
}
