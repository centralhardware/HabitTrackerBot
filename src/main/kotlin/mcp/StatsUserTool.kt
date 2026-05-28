package mcp

import CheckInService
import UserSettingsService
import dev.inmo.kslog.common.KSLog
import dev.inmo.kslog.common.info
import dto.McpJson
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import java.time.LocalDate
import java.time.ZoneOffset

object StatsUserTool : McpTool {
    override val name = "stats_user"
    override val description = "Return statistics for every active habit (today in the user's timezone)."
    override val inputSchema: ToolSchema = emptyObjectSchema()

    override fun handle(userId: Long, request: CallToolRequest): CallToolResult {
        logCall(userId, name, null)
        return try {
            val today = LocalDate.now(UserSettingsService.getTimezone(userId) ?: ZoneOffset.UTC)
            val stats = CheckInService.userStats(userId, today)
            KSLog.info("mcp $name user=$userId returned=${stats.size}")
            ok(McpJson.encodeToString(stats))
        } catch (e: Throwable) {
            crashed(userId, name, null, e)
        }
    }
}
