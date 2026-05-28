package mcp

import HabitService
import dev.inmo.kslog.common.KSLog
import dev.inmo.kslog.common.info
import dto.McpJson
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema

object HabitsListTool : McpTool {
    override val name = "habits_list"
    override val description = "List the authenticated user's active habits."
    override val inputSchema: ToolSchema = emptyObjectSchema()

    override fun handle(userId: Long, request: CallToolRequest): CallToolResult {
        logCall(userId, name, null)
        return try {
            val habits = HabitService.listActive(userId)
            KSLog.info("mcp $name user=$userId returned=${habits.size}")
            ok(McpJson.encodeToString(habits))
        } catch (e: Throwable) {
            crashed(userId, name, null, e)
        }
    }
}
