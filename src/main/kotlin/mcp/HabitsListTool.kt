package mcp

import services.HabitService
import Lang
import dto.McpJson
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import java.time.ZoneId

object HabitsListTool : McpTool {
    override val name = "habits_list"
    override val description = "List the authenticated user's active habits."
    override val inputSchema: ToolSchema = emptyObjectSchema()

    override fun handle(userId: Long, lang: Lang, tz: ZoneId, request: CallToolRequest): CallToolResult {
        val habits = HabitService.listActive(userId)
        return ok(McpJson.encodeToString(habits))
    }
}
