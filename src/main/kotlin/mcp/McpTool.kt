package mcp

import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema

interface McpTool {
    val name: String
    val description: String
    val inputSchema: ToolSchema
    fun handle(userId: Long, request: CallToolRequest): CallToolResult
}
