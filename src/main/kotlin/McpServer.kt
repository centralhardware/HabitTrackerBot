import dto.CheckinStatus
import dto.Habit
import dto.HabitStat
import dto.HabitType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.header
import io.ktor.server.request.path
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.util.AttributeKey
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.mcpStatelessStreamableHttp
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

private val UserIdKey = AttributeKey<Long>("mcpUserId")
private val TimeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

object McpServer {

    fun start(): EmbeddedServer<*, *> {
        val app = embeddedServer(CIO, host = Config.MCP_HOST, port = Config.MCP_PORT) {
            intercept(ApplicationCallPipeline.Plugins) {
                if (!context.request.path().startsWith("/mcp")) return@intercept
                val userId = McpTokenService.authenticate(context.request.header(HttpHeaders.Authorization))
                if (userId == null) {
                    context.response.header(HttpHeaders.WWWAuthenticate, "Bearer realm=\"mcp\"")
                    context.respond(HttpStatusCode.Unauthorized)
                    finish()
                    return@intercept
                }
                context.attributes.put(UserIdKey, userId)
            }
            mcpStatelessStreamableHttp(
                path = "/mcp",
                enableDnsRebindingProtection = false,
            ) {
                val userId = call.attributes[UserIdKey]
                buildServer(userId)
            }
        }
        return app.start(wait = false)
    }

    private fun buildServer(userId: Long): Server {
        val server = Server(
            serverInfo = Implementation(name = "habit-tracker", version = "1.0.0"),
            options = ServerOptions(
                capabilities = ServerCapabilities(
                    tools = ServerCapabilities.Tools(listChanged = false),
                ),
            ),
        )

        server.addTool(
            name = "habits_list",
            description = "List the authenticated user's active habits.",
            inputSchema = emptyObjectSchema(),
        ) { _ ->
            ok(buildJsonArray { HabitService.listActive(userId).forEach { add(it.toJson()) } }.toString())
        }

        server.addTool(
            name = "checkin_record",
            description = "Record a check-in. counter: value is an integer count 1..100 (default 1). quantity: value is the amount (>0). scheduled: status is 'done' (default) or 'skip'; if the habit has more than one reminder, pass reminderTime (HH:MM). Date is optional (YYYY-MM-DD), defaults to today in the user's timezone. Future dates are rejected; for scheduled habits, the reminder slot must have already fired today.",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    putJsonObject("habitId") { put("type", "integer") }
                    putJsonObject("value") { put("type", "number"); put("exclusiveMinimum", 0) }
                    putJsonObject("date") { put("type", "string"); put("pattern", "^\\d{4}-\\d{2}-\\d{2}$") }
                    putJsonObject("reminderTime") { put("type", "string"); put("pattern", "^[0-2][0-9]:[0-5][0-9]$") }
                    putJsonObject("status") {
                        put("type", "string")
                        putJsonArray("enum") { add("done"); add("skip") }
                    }
                },
                required = listOf("habitId"),
            ),
        ) { request ->
            val args = request.arguments ?: return@addTool err("arguments required")
            val habitId = args.lng("habitId") ?: return@addTool err("'habitId' is required")
            val habit = HabitService.findById(habitId, userId) ?: return@addTool err("Habit $habitId not found")
            val tz = UserSettingsService.getTimezone(userId) ?: ZoneOffset.UTC
            val nowLocal = ZonedDateTime.now(tz)
            val today = nowLocal.toLocalDate()
            val date = args.str("date")?.let {
                try { LocalDate.parse(it) } catch (_: DateTimeParseException) {
                    return@addTool err("Invalid date — use YYYY-MM-DD")
                }
            } ?: today
            if (date.isAfter(today)) return@addTool err("Cannot check in for a future date ($date > $today in $tz)")

            when (habit.type) {
                HabitType.SCHEDULED -> {
                    val reminders = HabitService.listReminders(habitId, userId)
                    if (reminders.isEmpty()) return@addTool err("Habit $habitId has no reminders configured")
                    val requestedTime = args.str("reminderTime")?.let {
                        try { LocalTime.parse(it, TimeFmt) } catch (_: DateTimeParseException) {
                            return@addTool err("Invalid reminderTime — use HH:MM")
                        }
                    }
                    val reminder = when {
                        requestedTime != null -> reminders.firstOrNull { it.time == requestedTime }
                            ?: return@addTool err("No reminder at $requestedTime; available: ${reminders.joinToString { it.time.format(TimeFmt) }}")
                        reminders.size == 1 -> reminders[0]
                        else -> return@addTool err("Habit has ${reminders.size} reminders; specify reminderTime (HH:MM). Available: ${reminders.joinToString { it.time.format(TimeFmt) }}")
                    }
                    if (date == today && nowLocal.toLocalTime() < reminder.time) {
                        return@addTool err("Reminder ${reminder.time.format(TimeFmt)} hasn't fired yet today")
                    }
                    val status = when (val raw = args.str("status")?.lowercase()) {
                        null, "done" -> CheckinStatus.DONE
                        "skip" -> CheckinStatus.SKIP
                        else -> return@addTool err("Invalid status '$raw' — use 'done' or 'skip'")
                    }
                    val recorded = CheckInService.record(reminder.id, userId, date, status)
                    if (!recorded) return@addTool err("Failed to record check-in")
                    ok("""{"recorded":true,"habitId":$habitId,"reminderId":${reminder.id},"date":"$date","status":"${status.value}"}""")
                }
                HabitType.COUNTER -> {
                    val count = (args.dbl("value") ?: 1.0).toInt()
                    if (count < 1 || count > 100) return@addTool err("'value' must be 1..100 for counter habits")
                    repeat(count) { CheckInService.checkInCounter(habitId, userId, date) }
                    ok("""{"recorded":true,"habitId":$habitId,"date":"$date","count":$count}""")
                }
                HabitType.QUANTITY -> {
                    val value = args.dbl("value") ?: return@addTool err("'value' is required for quantity habits")
                    if (value <= 0.0 || value.isNaN() || value.isInfinite()) return@addTool err("'value' must be > 0")
                    CheckInService.recordQuantity(habitId, userId, date, value)
                    ok("""{"recorded":true,"habitId":$habitId,"date":"$date","amount":$value}""")
                }
            }
        }

        server.addTool(
            name = "stats_user",
            description = "Return statistics for every active habit (today in the user's timezone).",
            inputSchema = emptyObjectSchema(),
        ) { _ ->
            val today = LocalDate.now(UserSettingsService.getTimezone(userId) ?: ZoneOffset.UTC)
            val stats = CheckInService.userStats(userId, today)
            ok(buildJsonArray { stats.forEach { add(it.toJson()) } }.toString())
        }

        return server
    }
}

private fun ok(text: String): CallToolResult =
    CallToolResult(content = listOf(TextContent(text)), isError = false)

private fun err(text: String): CallToolResult =
    CallToolResult(content = listOf(TextContent(text)), isError = true)

private fun emptyObjectSchema(): ToolSchema = ToolSchema(properties = JsonObject(emptyMap()))

private fun JsonObject.str(key: String): String? =
    (get(key) as? JsonPrimitive)?.contentOrNull

private fun JsonObject.dbl(key: String): Double? =
    (get(key) as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()

private fun JsonObject.lng(key: String): Long? =
    (get(key) as? JsonPrimitive)?.contentOrNull?.toLongOrNull()

private fun Habit.toJson(): JsonObject = buildJsonObject {
    put("id", id)
    put("name", name)
    put("type", type.value)
    put("status", status.value)
    dailyTarget?.let { put("dailyTarget", it) }
    unit?.let { put("unit", it) }
    direction?.let { put("direction", it.value) }
    putJsonArray("reminders") { reminders.forEach { add(it.format(TimeFmt)) } }
}

private fun HabitStat.toJson(): JsonObject = when (this) {
    is HabitStat.Scheduled -> buildJsonObject {
        put("kind", "scheduled")
        put("habitId", habitId); put("name", name)
        put("totalDays", totalDays); put("doneCount", doneCount); put("skipCount", skipCount)
        put("streak", streak)
    }
    is HabitStat.Counter.WithTarget -> buildJsonObject {
        put("kind", "counter.withTarget")
        put("habitId", habitId); put("name", name)
        put("dailyTarget", dailyTarget); direction?.let { put("direction", it.value) }
        put("todayCount", todayCount); put("doneDays", doneDays); put("skipDays", skipDays); put("streak", streak)
    }
    is HabitStat.Counter.Trend -> buildJsonObject {
        put("kind", "counter.trend")
        put("habitId", habitId); put("name", name); put("direction", direction.value)
        put("todayCount", todayCount); put("yesterdayCount", yesterdayCount)
        put("grandTotal", grandTotal); put("daysLogged", daysLogged); put("overallAvg", overallAvg)
        put("recent3Avg", recent3Avg); put("previous3Avg", previous3Avg)
        put("recent7Avg", recent7Avg); put("previous7Avg", previous7Avg)
    }
    is HabitStat.Counter.Plain -> buildJsonObject {
        put("kind", "counter.plain")
        put("habitId", habitId); put("name", name)
        put("todayCount", todayCount); put("grandTotal", grandTotal); put("daysLogged", daysLogged)
    }
    is HabitStat.Quantity.WithTarget -> buildJsonObject {
        put("kind", "quantity.withTarget")
        put("habitId", habitId); put("name", name); unit?.let { put("unit", it) }
        put("dailyTarget", dailyTarget); direction?.let { put("direction", it.value) }
        put("todayTotal", todayTotal); put("doneDays", doneDays); put("skipDays", skipDays); put("streak", streak)
    }
    is HabitStat.Quantity.Trend -> buildJsonObject {
        put("kind", "quantity.trend")
        put("habitId", habitId); put("name", name); unit?.let { put("unit", it) }
        put("direction", direction.value)
        put("todayTotal", todayTotal); put("yesterdayTotal", yesterdayTotal)
        put("grandTotal", grandTotal); put("daysLogged", daysLogged); put("overallAvg", overallAvg)
        put("recent3Avg", recent3Avg); put("previous3Avg", previous3Avg)
        put("recent7Avg", recent7Avg); put("previous7Avg", previous7Avg)
    }
    is HabitStat.Quantity.Plain -> buildJsonObject {
        put("kind", "quantity.plain")
        put("habitId", habitId); put("name", name); unit?.let { put("unit", it) }
        put("todayTotal", todayTotal); put("grandTotal", grandTotal); put("daysLogged", daysLogged)
    }
}
