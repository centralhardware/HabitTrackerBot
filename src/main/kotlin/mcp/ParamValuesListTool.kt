package mcp

import Lang
import dto.McpJson
import dto.ParamValuesListArgs
import services.HabitService
import services.ParamValueService
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ToolAnnotations
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.time.ZoneId

object ParamValuesListTool : TypedMcpTool<ParamValuesListArgs>(ParamValuesListArgs.serializer()) {
    override val name = "param_values_list"
    override val description =
        "List the distinct values stored for a low-cardinality param, with a usage count for each (most-used " +
            "first). Use this before param_values_merge to see the exact strings (their casing/spelling) and decide " +
            "which near-duplicates to fold together. 'habitId' and 'paramId' come from habits_list; the param must be " +
            "low-cardinality (lowCardinality=true). Returns [{ value, uses }]."
    override val inputSchema: ToolSchema = buildSchema()
    override val annotations = ToolAnnotations(readOnlyHint = true, openWorldHint = false)

    override fun handle(userId: Long, lang: Lang, tz: ZoneId, args: ParamValuesListArgs): CallToolResult {
        val habit = HabitService.findById(args.habitId, userId)
            ?: return err("Habit ${args.habitId} not found")
        val param = habit.params.firstOrNull { it.id == args.paramId }
            ?: return err("Param ${args.paramId} is not a field of habit ${args.habitId}")
        if (!param.lowCardinality) return err("Param ${args.paramId} is not low-cardinality; it has no value dictionary")

        return ok(McpJson.encodeToString(ParamValueService.listValues(args.paramId)))
    }

    private fun buildSchema(): ToolSchema {
        val props = buildJsonObject {
            putJsonObject("habitId") {
                put("type", "integer")
                put("description", "The habit's id (from habits_list).")
            }
            putJsonObject("paramId") {
                put("type", "integer")
                put("description", "A low-cardinality param id from habits_list params[].id of this habit.")
            }
        }
        return ToolSchema(properties = props, required = listOf("habitId", "paramId"))
    }
}
