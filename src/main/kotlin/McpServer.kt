import dev.inmo.kslog.common.KSLog
import dev.inmo.kslog.common.info
import dev.inmo.kslog.common.warning
import dto.CheckinRecordArgs
import dto.CheckinStatus
import dto.CheckinsListArgs
import dto.HabitType
import dto.McpJson
import dto.McpProp
import dto.QuantityGroupRecordArgs
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
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
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
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
            logCall(userId, "habits_list", null)
            val habits = HabitService.listActive(userId)
            KSLog.info("mcp habits_list user=$userId returned=${habits.size}")
            ok(McpJson.encodeToString(habits))
        }

        server.addTool(
            name = "checkin_record",
            description = "Record a check-in. counter: value is an integer count 1..100 (default 1). quantity (single-field): value is the amount (>0); optional comment attaches a free-form note. For multi-field quantity habits prefer 'quantity_group_record' — it writes all fields with one shared comment in a single call; this tool will reject the group root's habitId. scheduled: status is 'done' (default) or 'skip'; if the habit has more than one reminder, pass reminderTime (HH:MM). Date is optional (YYYY-MM-DD), defaults to today in the user's timezone. Future dates are rejected; for scheduled habits, the reminder slot must have already fired today. 'comment' is only allowed for quantity habits.",
            inputSchema = toolSchema(
                mapOf(
                    "habitId" to McpProp(type = "integer"),
                    "value" to McpProp(type = "number", exclusiveMinimum = 0),
                    "date" to McpProp(type = "string", pattern = "^\\d{4}-\\d{2}-\\d{2}$"),
                    "reminderTime" to McpProp(type = "string", pattern = "^[0-2][0-9]:[0-5][0-9]$"),
                    "status" to McpProp(type = "string", enum = listOf("done", "skip")),
                    "comment" to McpProp(type = "string"),
                ),
                required = listOf("habitId"),
            ),
        ) { request ->
            val rawArgs = request.arguments ?: return@addTool failed(userId, "checkin_record", null, "arguments required")
            logCall(userId, "checkin_record", rawArgs)
            val args = runCatching { McpJson.decodeFromJsonElement<CheckinRecordArgs>(rawArgs) }
                .getOrElse { return@addTool failed(userId, "checkin_record", rawArgs, "Invalid arguments: ${it.message}") }
            val habit = HabitService.findById(args.habitId, userId) ?: return@addTool failed(userId, "checkin_record", rawArgs, "Habit ${args.habitId} not found")
            val tz = UserSettingsService.getTimezone(userId) ?: ZoneOffset.UTC
            val nowLocal = ZonedDateTime.now(tz)
            val today = nowLocal.toLocalDate()
            val date = args.date?.let {
                try { LocalDate.parse(it) } catch (_: DateTimeParseException) {
                    return@addTool failed(userId, "checkin_record", rawArgs, "Invalid date — use YYYY-MM-DD")
                }
            } ?: today
            if (date.isAfter(today)) return@addTool failed(userId, "checkin_record", rawArgs, "Cannot check in for a future date ($date > $today in $tz)")

            val comment = args.comment?.trim()?.ifEmpty { null }
            if (comment != null && habit.type != HabitType.QUANTITY) {
                return@addTool failed(userId, "checkin_record", rawArgs, "'comment' is only supported for quantity habits")
            }

            when (habit.type) {
                HabitType.SCHEDULED -> {
                    val reminders = HabitService.listReminders(args.habitId, userId)
                    if (reminders.isEmpty()) return@addTool failed(userId, "checkin_record", rawArgs, "Habit ${args.habitId} has no reminders configured")
                    val requestedTime = args.reminderTime?.let {
                        try { LocalTime.parse(it, TimeFmt) } catch (_: DateTimeParseException) {
                            return@addTool failed(userId, "checkin_record", rawArgs, "Invalid reminderTime — use HH:MM")
                        }
                    }
                    val reminder = when {
                        requestedTime != null -> reminders.firstOrNull { it.time == requestedTime }
                            ?: return@addTool failed(userId, "checkin_record", rawArgs, "No reminder at $requestedTime; available: ${reminders.joinToString { it.time.format(TimeFmt) }}")
                        reminders.size == 1 -> reminders[0]
                        else -> return@addTool failed(userId, "checkin_record", rawArgs, "Habit has ${reminders.size} reminders; specify reminderTime (HH:MM). Available: ${reminders.joinToString { it.time.format(TimeFmt) }}")
                    }
                    if (date == today && nowLocal.toLocalTime() < reminder.time) {
                        return@addTool failed(userId, "checkin_record", rawArgs, "Reminder ${reminder.time.format(TimeFmt)} hasn't fired yet today")
                    }
                    val status = when (val raw = args.status?.lowercase()) {
                        null, "done" -> CheckinStatus.DONE
                        "skip" -> CheckinStatus.SKIP
                        else -> return@addTool failed(userId, "checkin_record", rawArgs, "Invalid status '$raw' — use 'done' or 'skip'")
                    }
                    val recorded = CheckInService.record(reminder.id, userId, date, status)
                    if (!recorded) return@addTool failed(userId, "checkin_record", rawArgs, "Failed to record check-in")
                    KSLog.info("mcp checkin_record user=$userId habit=${args.habitId} reminder=${reminder.id} date=$date status=${status.value}")
                    ok("""{"recorded":true,"habitId":${args.habitId},"reminderId":${reminder.id},"date":"$date","status":"${status.value}"}""")
                }
                HabitType.COUNTER -> {
                    val count = (args.value ?: 1.0).toInt()
                    if (count < 1 || count > 100) return@addTool failed(userId, "checkin_record", rawArgs, "'value' must be 1..100 for counter habits")
                    repeat(count) { CheckInService.checkInCounter(args.habitId, userId, date) }
                    KSLog.info("mcp checkin_record user=$userId habit=${args.habitId} date=$date count=$count")
                    ok("""{"recorded":true,"habitId":${args.habitId},"date":"$date","count":$count}""")
                }
                HabitType.QUANTITY -> {
                    if (habit.isGroupRoot) {
                        return@addTool failed(userId, "checkin_record", rawArgs,
                            "Habit ${args.habitId} is a multi-field group root; call checkin_record per field (see fields[].id from habits_list)")
                    }
                    val value = args.value ?: return@addTool failed(userId, "checkin_record", rawArgs, "'value' is required for quantity habits")
                    if (value <= 0.0 || value.isNaN() || value.isInfinite()) return@addTool failed(userId, "checkin_record", rawArgs, "'value' must be > 0")
                    CheckInService.recordQuantity(args.habitId, userId, date, value, comment)
                    KSLog.info("mcp checkin_record user=$userId habit=${args.habitId} date=$date amount=$value comment=${comment != null}")
                    ok("""{"recorded":true,"habitId":${args.habitId},"date":"$date","amount":$value}""")
                }
            }
        }

        server.addTool(
            name = "quantity_group_record",
            description = "Record a multi-field quantity check-in in one call: writes one value per field under a single shared comment (one row in the comments table referenced by every checkin row of this event). 'habitId' is the group root id (where habits_list returns isGroupRoot/fields). 'values' is an array of { fieldId, value }; fieldId must be one of root.fields[].id; value must be > 0. Date is optional (YYYY-MM-DD), defaults to today in the user's timezone; future dates are rejected. 'comment' is optional and applies to the whole event.",
            inputSchema = quantityGroupRecordSchema(),
        ) { request ->
            val rawArgs = request.arguments ?: return@addTool failed(userId, "quantity_group_record", null, "arguments required")
            logCall(userId, "quantity_group_record", rawArgs)
            val args = runCatching { McpJson.decodeFromJsonElement<QuantityGroupRecordArgs>(rawArgs) }
                .getOrElse { return@addTool failed(userId, "quantity_group_record", rawArgs, "Invalid arguments: ${it.message}") }
            val root = HabitService.findById(args.habitId, userId)
                ?: return@addTool failed(userId, "quantity_group_record", rawArgs, "Habit ${args.habitId} not found")
            if (!root.isGroupRoot) {
                return@addTool failed(userId, "quantity_group_record", rawArgs,
                    "Habit ${args.habitId} is not a multi-field group root; use checkin_record for single quantity habits")
            }
            if (args.values.isEmpty()) {
                return@addTool failed(userId, "quantity_group_record", rawArgs, "'values' must be non-empty")
            }
            val tz = UserSettingsService.getTimezone(userId) ?: ZoneOffset.UTC
            val today = LocalDate.now(tz)
            val date = args.date?.let {
                try { LocalDate.parse(it) } catch (_: DateTimeParseException) {
                    return@addTool failed(userId, "quantity_group_record", rawArgs, "Invalid date — use YYYY-MM-DD")
                }
            } ?: today
            if (date.isAfter(today)) {
                return@addTool failed(userId, "quantity_group_record", rawArgs, "Cannot check in for a future date ($date > $today in $tz)")
            }

            val allowedIds = root.fields.map { it.id }.toSet()
            val unknown = args.values.map { it.fieldId }.filter { it !in allowedIds }
            if (unknown.isNotEmpty()) {
                return@addTool failed(userId, "quantity_group_record", rawArgs,
                    "Unknown fieldId(s) ${unknown.joinToString()}; allowed: ${allowedIds.joinToString()}")
            }
            args.values.firstOrNull { it.value <= 0.0 || it.value.isNaN() || it.value.isInfinite() }?.let {
                return@addTool failed(userId, "quantity_group_record", rawArgs, "All 'value' entries must be > 0 (fieldId ${it.fieldId})")
            }

            val map = args.values.associate { it.fieldId to it.value }
            val comment = args.comment?.trim()?.ifEmpty { null }
            val wrote = CheckInService.recordQuantityGroup(args.habitId, userId, date, map, comment)
            KSLog.info("mcp quantity_group_record user=$userId root=${args.habitId} date=$date fields=${map.size} wrote=$wrote comment=${comment != null}")
            ok("""{"recorded":true,"habitId":${args.habitId},"date":"$date","fields":${map.size},"wrote":$wrote}""")
        }

        server.addTool(
            name = "checkins_list",
            description = "List past check-ins for a habit between two dates (inclusive). Defaults: from = today - 30 days, to = today (in the user's timezone). Maximum range 366 days. Returns each row with date, status (done/skip/null for pending), quantity (for quantity habits), reminderTime (for scheduled habits), and comment (for quantity habits, when set).",
            inputSchema = toolSchema(
                mapOf(
                    "habitId" to McpProp(type = "integer"),
                    "from" to McpProp(type = "string", pattern = "^\\d{4}-\\d{2}-\\d{2}$"),
                    "to" to McpProp(type = "string", pattern = "^\\d{4}-\\d{2}-\\d{2}$"),
                ),
                required = listOf("habitId"),
            ),
        ) { request ->
            val rawArgs = request.arguments ?: return@addTool failed(userId, "checkins_list", null, "arguments required")
            logCall(userId, "checkins_list", rawArgs)
            val args = runCatching { McpJson.decodeFromJsonElement<CheckinsListArgs>(rawArgs) }
                .getOrElse { return@addTool failed(userId, "checkins_list", rawArgs, "Invalid arguments: ${it.message}") }
            val tz = UserSettingsService.getTimezone(userId) ?: ZoneOffset.UTC
            val today = LocalDate.now(tz)
            val to = args.to?.let {
                try { LocalDate.parse(it) } catch (_: DateTimeParseException) {
                    return@addTool failed(userId, "checkins_list", rawArgs, "Invalid 'to' — use YYYY-MM-DD")
                }
            } ?: today
            val from = args.from?.let {
                try { LocalDate.parse(it) } catch (_: DateTimeParseException) {
                    return@addTool failed(userId, "checkins_list", rawArgs, "Invalid 'from' — use YYYY-MM-DD")
                }
            } ?: to.minusDays(30)
            if (from.isAfter(to)) return@addTool failed(userId, "checkins_list", rawArgs, "'from' must be on or before 'to'")
            if (java.time.temporal.ChronoUnit.DAYS.between(from, to) > 366) {
                return@addTool failed(userId, "checkins_list", rawArgs, "Range too large; max 366 days")
            }
            val rows = CheckInService.listInRange(args.habitId, userId, from, to)
                ?: return@addTool failed(userId, "checkins_list", rawArgs, "Habit ${args.habitId} not found")
            KSLog.info("mcp checkins_list user=$userId habit=${args.habitId} range=$from..$to returned=${rows.size}")
            ok(McpJson.encodeToString(rows))
        }

        server.addTool(
            name = "stats_user",
            description = "Return statistics for every active habit (today in the user's timezone).",
            inputSchema = emptyObjectSchema(),
        ) { _ ->
            logCall(userId, "stats_user", null)
            val today = LocalDate.now(UserSettingsService.getTimezone(userId) ?: ZoneOffset.UTC)
            val stats = CheckInService.userStats(userId, today)
            KSLog.info("mcp stats_user user=$userId returned=${stats.size}")
            ok(McpJson.encodeToString(stats))
        }

        return server
    }
}

private fun ok(text: String): CallToolResult =
    CallToolResult(content = listOf(TextContent(text)), isError = false)

private fun err(text: String): CallToolResult =
    CallToolResult(content = listOf(TextContent(text)), isError = true)

private fun logCall(userId: Long, tool: String, args: JsonObject?) {
    KSLog.info("mcp call user=$userId tool=$tool args=${args ?: "{}"}")
}

private fun failed(userId: Long, tool: String, args: JsonObject?, reason: String): CallToolResult {
    KSLog.warning("mcp fail user=$userId tool=$tool args=${args ?: "{}"} reason=$reason")
    return err(reason)
}

private fun emptyObjectSchema(): ToolSchema = ToolSchema(properties = JsonObject(emptyMap()))

private fun toolSchema(props: Map<String, McpProp>, required: List<String> = emptyList()): ToolSchema =
    ToolSchema(
        properties = McpJson.encodeToJsonElement(props).jsonObject,
        required = required,
    )

private fun quantityGroupRecordSchema(): ToolSchema {
    val props = buildJsonObject {
        putJsonObject("habitId") { put("type", "integer") }
        putJsonObject("values") {
            put("type", "array")
            put("minItems", 1)
            putJsonObject("items") {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("fieldId") { put("type", "integer") }
                    putJsonObject("value") {
                        put("type", "number")
                        put("exclusiveMinimum", 0)
                    }
                }
                putJsonArray("required") {
                    add("fieldId"); add("value")
                }
            }
        }
        putJsonObject("date") {
            put("type", "string")
            put("pattern", "^\\d{4}-\\d{2}-\\d{2}$")
        }
        putJsonObject("comment") { put("type", "string") }
    }
    return ToolSchema(properties = props, required = listOf("habitId", "values"))
}

