package mcp

import BotNotifier
import HabitService
import Lang
import Strings
import dto.HabitIdArgs
import dto.McpJson
import dto.McpProp
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.decodeFromJsonElement
import java.time.ZoneId

object HabitDeleteTool : McpTool {
    override val name = "habit_delete"
    override val description =
        "Delete a habit (soft delete — it stops firing reminders and disappears from lists, but past check-ins are retained). 'habitId' is the habit id (group root for multi-field habits). This cannot be undone via MCP."
    override val inputSchema: ToolSchema = toolSchema(
        mapOf("habitId" to McpProp(type = "integer")),
        required = listOf("habitId"),
    )

    override fun handle(userId: Long, lang: Lang, tz: ZoneId, request: CallToolRequest): CallToolResult {
        val rawArgs = request.arguments ?: return err("arguments required")
        val args = runCatching { McpJson.decodeFromJsonElement<HabitIdArgs>(rawArgs) }
            .getOrElse { return err("Invalid arguments: ${it.message}") }
        val habit = HabitService.findById(args.habitId, userId)
            ?: return err("Habit ${args.habitId} not found")
        if (!HabitService.softDelete(args.habitId, userId)) {
            return err("Failed to delete habit ${args.habitId}")
        }
        BotNotifier.notify(userId, Strings.mcpDeletedHabit(lang, habit.name))
        return ok("""{"deleted":true,"habitId":${args.habitId}}""")
    }
}
