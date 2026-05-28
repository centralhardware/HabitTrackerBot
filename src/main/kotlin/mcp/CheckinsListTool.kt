package mcp

import CheckInService
import UserSettingsService
import dev.inmo.kslog.common.KSLog
import dev.inmo.kslog.common.info
import dto.CheckinsListArgs
import dto.McpJson
import dto.McpProp
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.decodeFromJsonElement
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

object CheckinsListTool : McpTool {
    override val name = "checkins_list"
    override val description =
        "List past check-ins for a habit between two dates (inclusive). Defaults: from = today - 30 days, to = today (in the user's timezone). Maximum range 366 days. Returns each row with date, status (done/skip/null for pending), quantity (for quantity habits), reminderTime (for scheduled habits), and comment (for quantity habits, when set)."
    override val inputSchema: ToolSchema = toolSchema(
        mapOf(
            "habitId" to McpProp(type = "integer"),
            "from" to McpProp(type = "string", pattern = "^\\d{4}-\\d{2}-\\d{2}$"),
            "to" to McpProp(type = "string", pattern = "^\\d{4}-\\d{2}-\\d{2}$"),
        ),
        required = listOf("habitId"),
    )

    override fun handle(userId: Long, request: CallToolRequest): CallToolResult {
        val rawArgs = request.arguments ?: return failed(userId, name, null, "arguments required")
        logCall(userId, name, rawArgs)
        return try {
            val args = runCatching { McpJson.decodeFromJsonElement<CheckinsListArgs>(rawArgs) }
                .getOrElse { return failed(userId, name, rawArgs, "Invalid arguments: ${it.message}") }
            val tz = UserSettingsService.getTimezone(userId) ?: ZoneOffset.UTC
            val today = LocalDate.now(tz)
            val to = args.to?.let {
                try { LocalDate.parse(it) } catch (_: DateTimeParseException) {
                    return failed(userId, name, rawArgs, "Invalid 'to' — use YYYY-MM-DD")
                }
            } ?: today
            val from = args.from?.let {
                try { LocalDate.parse(it) } catch (_: DateTimeParseException) {
                    return failed(userId, name, rawArgs, "Invalid 'from' — use YYYY-MM-DD")
                }
            } ?: to.minusDays(30)
            if (from.isAfter(to)) return failed(userId, name, rawArgs, "'from' must be on or before 'to'")
            if (ChronoUnit.DAYS.between(from, to) > 366) {
                return failed(userId, name, rawArgs, "Range too large; max 366 days")
            }
            val rows = CheckInService.listInRange(args.habitId, userId, from, to)
                ?: return failed(userId, name, rawArgs, "Habit ${args.habitId} not found")
            KSLog.info("mcp $name user=$userId habit=${args.habitId} range=$from..$to returned=${rows.size}")
            ok(McpJson.encodeToString(rows))
        } catch (e: Throwable) {
            crashed(userId, name, rawArgs, e)
        }
    }
}
