package mcp

import services.CheckInService
import Lang
import dto.McpJson
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ToolAnnotations
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import java.time.LocalDate
import java.time.ZoneId

object StatsUserTool : McpTool {
    override val name = "stats_user"
    override val description =
        "Return per-habit statistics for every active habit, computed for today in the user's timezone. Each entry has " +
            "habitId, name, streak (consecutive days), loggedDays/totalDays over the window, and for quantity habits a " +
            "trend (today vs recentAvg vs overallAvg, with unit/direction). Multi-field habits break down into " +
            "groupFields[]. Use this for progress questions; use habits_list when you only need ids/config."
    override val inputSchema: ToolSchema = emptyObjectSchema()
    override val annotations = ToolAnnotations(readOnlyHint = true, openWorldHint = false)

    override fun handle(userId: Long, lang: Lang, tz: ZoneId, request: CallToolRequest): CallToolResult {
        val today = LocalDate.now(tz)
        val stats = CheckInService.userStats(userId, today)
        return ok(McpJson.encodeToString(stats))
    }
}
