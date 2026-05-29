package mcp

import CheckInService
import Lang
import dto.CheckinsListArgs
import dto.McpJson
import dto.McpProp
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

object CheckinsListTool : TypedMcpTool<CheckinsListArgs>(CheckinsListArgs.serializer()) {
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

    override fun handle(userId: Long, lang: Lang, tz: ZoneId, args: CheckinsListArgs): CallToolResult {
        val today = LocalDate.now(tz)
        val to = args.to?.let {
            try { LocalDate.parse(it) } catch (_: DateTimeParseException) {
                return err("Invalid 'to' — use YYYY-MM-DD")
            }
        } ?: today
        val from = args.from?.let {
            try { LocalDate.parse(it) } catch (_: DateTimeParseException) {
                return err("Invalid 'from' — use YYYY-MM-DD")
            }
        } ?: to.minusDays(30)
        if (from.isAfter(to)) return err("'from' must be on or before 'to'")
        if (ChronoUnit.DAYS.between(from, to) > 366) {
            return err("Range too large; max 366 days")
        }
        val rows = CheckInService.listInRange(args.habitId, userId, from, to)
            ?: return err("Habit ${args.habitId} not found")
        return ok(McpJson.encodeToString(rows))
    }
}
