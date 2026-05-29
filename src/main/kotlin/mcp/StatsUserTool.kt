package mcp

import CheckInService
import Lang
import dto.McpJson
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import java.time.LocalDate
import java.time.ZoneId

object StatsUserTool : McpTool {
    override val name = "stats_user"
    override val description = "Return statistics for every active habit (today in the user's timezone)."
    override val inputSchema: ToolSchema = emptyObjectSchema()

    override fun handle(userId: Long, lang: Lang, tz: ZoneId, request: CallToolRequest): CallToolResult {
        val today = LocalDate.now(tz)
        val stats = CheckInService.userStats(userId, today)
        return ok(McpJson.encodeToString(stats))
    }
}
