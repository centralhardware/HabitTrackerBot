package mcp

import services.CheckInService
import Lang
import dto.CheckinsListArgs
import dto.CheckinsListResult
import dto.HabitCheckins
import dto.McpJson
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ToolAnnotations
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

object CheckinsListTool : TypedMcpTool<CheckinsListArgs>(CheckinsListArgs.serializer()) {
    override val name = "checkins_list"
    override val description =
        "List past check-ins for one or more habits between two dates (inclusive). 'habitIds' is an array of habit ids " +
            "(batch query). The response is { from, to, habits[] } where from/to echo the resolved window and habits[] " +
            "has one entry per id. Defaults: from = today - 30 days, to = today (in the user's " +
            "timezone). Maximum range 366 days. Each check-in row has checkinId (pass it to checkin_delete to remove the " +
            "entry), paramId (which field of a multi-field habit it belongs to; see habits_list params), date, status " +
            "(done/skip/null for pending), quantity (for quantity habits), reminderTime (for scheduled habits), and " +
            "comment (when set). Unknown habit ids are returned with found=false."
    override val inputSchema: ToolSchema = buildSchema()
    override val annotations = ToolAnnotations(readOnlyHint = true, openWorldHint = false)

    override fun handle(userId: Long, lang: Lang, tz: ZoneId, args: CheckinsListArgs): CallToolResult {
        if (args.habitIds.isEmpty()) return err("'habitIds' must be non-empty")
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
        val habits = args.habitIds.distinct().map { habitId ->
            val rows = CheckInService.listInRange(habitId, userId, from, to)
            HabitCheckins(habitId = habitId, found = rows != null, checkins = rows ?: emptyList())
        }
        val result = CheckinsListResult(from = from.toString(), to = to.toString(), habits = habits)
        return ok(McpJson.encodeToString(result))
    }

    private fun buildSchema(): ToolSchema {
        val props = buildJsonObject {
            putJsonObject("habitIds") {
                put("type", "array")
                put("description", "Habit ids to fetch (from habits_list); one result entry per id.")
                putJsonObject("items") { put("type", "integer") }
            }
            putJsonObject("from") {
                put("type", "string")
                put("format", "date")
                put("pattern", "^\\d{4}-\\d{2}-\\d{2}$")
                put("description", "Start date, inclusive (YYYY-MM-DD). Default: 'to' minus 30 days.")
            }
            putJsonObject("to") {
                put("type", "string")
                put("format", "date")
                put("pattern", "^\\d{4}-\\d{2}-\\d{2}$")
                put("description", "End date, inclusive (YYYY-MM-DD). Default: today in the user's timezone.")
            }
        }
        return ToolSchema(properties = props, required = listOf("habitIds"))
    }
}
