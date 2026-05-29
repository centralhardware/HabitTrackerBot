package mcp

import Lang
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import java.time.ZoneId

interface McpTool {
    val name: String
    val description: String
    val inputSchema: ToolSchema
    fun handle(userId: Long, lang: Lang, tz: ZoneId, request: CallToolRequest): CallToolResult
}
