package mcp

import BotNotifier
import Lang
import Strings
import dto.Direction
import dto.ParamType
import dto.TimerPhase
import dto.Track
import dto.TrackCreateArgs
import dto.TrackFieldArg
import dto.TrackParam
import dto.TrackReminder
import dto.TrackType
import services.TrackService
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ToolAnnotations
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.time.ZoneId

object TrackCreateTool : TypedMcpTool<TrackCreateArgs>(TrackCreateArgs.serializer()) {
    override val name = "track_create"
    override val description =
        "Create a new track for the authenticated user. 'name' (non-blank) and 'type' are required. " +
            "'type': \"check\" (scheduled done/skip track — requires at least one reminder; takes no fields/unit/target), " +
            "\"quantity\" (one or more numeric/text fields — pass 'params', or leave it empty with top-level " +
            "unit/dailyTarget/direction for a single numeric field), or \"timer\" (tracks elapsed time; optional " +
            "top-level dailyTarget in seconds/unit/direction and optional 'params' as before/after annotation fields; " +
            "takes no reminders). 'logOnly'=true makes it a journal (no targets/streaks/stats). " +
            "'params' items: { name, paramType (\"number\"|\"text\"), unit, dailyTarget, direction (\"more\"|\"less\"), " +
            "timerPhase (\"before\"|\"after\", timers only) }. 'reminders' items: a time \"HH:MM\" (hours 0–47, 24+ = " +
            "next day) or raw 'offsetMinutes' (0–2879), plus optional 'days' (ISO 1=Mon..7=Sun; empty = every day). " +
            "Returns the new trackId. (Read tracks with tracks_list; edit with track_update.)"
    override val inputSchema: ToolSchema = buildSchema()
    override val annotations = ToolAnnotations(
        readOnlyHint = false,
        destructiveHint = false,
        idempotentHint = false,
        openWorldHint = false,
    )

    override fun handle(userId: Long, lang: Lang, tz: ZoneId, args: TrackCreateArgs): CallToolResult {
        val name = args.name.trim()
        if (name.isEmpty()) return err("'name' must be non-blank")

        val type = when (args.type.trim().lowercase()) {
            "check" -> TrackType.CHECK
            "quantity" -> TrackType.QUANTITY
            "timer" -> TrackType.TIMER
            else -> return err("'type' must be one of: check, quantity, timer")
        }

        val reminders = when (val r = resolveReminders(args.reminders)) {
            is ReminderResolve.Fail -> return err(r.msg)
            is ReminderResolve.Ok -> r.reminders
        }

        val hasTopLevelMetric = args.unit != null || args.dailyTarget != null || args.direction != null

        // Build the track per type; metric metadata lives on params for quantity and on the track
        // row for timer (mirroring how the old add-track dialog persisted each type).
        val track: Track = when (type) {
            TrackType.CHECK -> {
                if (reminders.isEmpty()) return err("A check track needs at least one reminder")
                if (args.params.isNotEmpty() || hasTopLevelMetric)
                    return err("A check track takes no fields, unit, dailyTarget or direction")
                Track(userId = userId, name = name, type = type, reminders = reminders, logOnly = args.logOnly)
            }

            TrackType.QUANTITY -> {
                val params = if (args.params.isNotEmpty()) {
                    args.params.mapIndexed { i, f ->
                        when (val p = resolveField(f, timer = false)) {
                            is FieldResolve.Fail -> return err("params[$i]: ${p.msg}")
                            is FieldResolve.Ok -> p.param
                        }
                    }
                } else {
                    // Single numeric field from the top-level metric fields.
                    val dir = args.direction?.let { Direction.parse(it) ?: return err("'direction' must be \"more\" or \"less\"") }
                    args.dailyTarget?.let { if (it < 0 || it.isNaN() || it.isInfinite()) return err("'dailyTarget' must be ≥ 0 and finite") }
                    listOf(TrackParam(id = 0, name = name.take(64), unit = args.unit?.trim()?.ifEmpty { null },
                        direction = dir, dailyTarget = args.dailyTarget, paramType = ParamType.NUMBER))
                }
                Track(userId = userId, name = name, type = type, reminders = reminders, params = params, logOnly = args.logOnly)
            }

            TrackType.TIMER -> {
                if (reminders.isNotEmpty()) return err("Timer tracks take no reminders")
                val dir = args.direction?.let { Direction.parse(it) ?: return err("'direction' must be \"more\" or \"less\"") }
                args.dailyTarget?.let { if (it < 0 || it.isNaN() || it.isInfinite()) return err("'dailyTarget' must be ≥ 0 and finite") }
                val annotations = args.params.mapIndexed { i, f ->
                    if (f.timerPhase == null) return err("params[$i]: timer fields need a timerPhase (\"before\" or \"after\")")
                    when (val p = resolveField(f, timer = true)) {
                        is FieldResolve.Fail -> return err("params[$i]: ${p.msg}")
                        is FieldResolve.Ok -> p.param
                    }
                }
                // With annotation fields present the repository no longer auto-creates the timer's
                // duration param, so prepend it here (a phase-less NUMBER param). Empty = repo adds it.
                val params = if (annotations.isEmpty()) emptyList()
                else listOf(TrackParam(id = 0, paramType = ParamType.NUMBER)) + annotations
                Track(userId = userId, name = name, type = type, unit = args.unit?.trim()?.ifEmpty { null },
                    dailyTarget = args.dailyTarget, direction = dir, params = params, logOnly = args.logOnly)
            }
        }

        val saved = TrackService.addTrack(track)
        BotNotifier.notify(userId, "🤖 " + Strings.trackAddedDetailed(lang, saved))
        return ok("""{"created":true,"trackId":${saved.id}}""")
    }

    private sealed interface FieldResolve {
        data class Ok(val param: TrackParam) : FieldResolve
        data class Fail(val msg: String) : FieldResolve
    }

    private fun resolveField(f: TrackFieldArg, timer: Boolean): FieldResolve {
        val paramType = ParamType.parse(f.paramType) ?: return FieldResolve.Fail("paramType must be \"number\" or \"text\"")
        val phase = f.timerPhase?.let { TimerPhase.parse(it) ?: return FieldResolve.Fail("timerPhase must be \"before\" or \"after\"") }
        val fname = f.name?.trim()?.ifEmpty { null }
        if (fname == null && (timer || paramType == ParamType.TEXT))
            return FieldResolve.Fail("field needs a 'name'")
        if (paramType == ParamType.TEXT && (f.unit != null || f.dailyTarget != null || f.direction != null))
            return FieldResolve.Fail("unit/dailyTarget/direction apply only to number fields")
        val dir = f.direction?.let { Direction.parse(it) ?: return FieldResolve.Fail("direction must be \"more\" or \"less\"") }
        f.dailyTarget?.let { if (it < 0 || it.isNaN() || it.isInfinite()) return FieldResolve.Fail("dailyTarget must be ≥ 0 and finite") }
        return FieldResolve.Ok(
            TrackParam(
                id = 0,
                name = fname?.take(64),
                unit = f.unit?.trim()?.ifEmpty { null },
                direction = dir,
                dailyTarget = f.dailyTarget,
                paramType = paramType,
                timerPhase = phase,
            )
        )
    }

    private sealed interface ReminderResolve {
        data class Ok(val reminders: List<TrackReminder>) : ReminderResolve
        data class Fail(val msg: String) : ReminderResolve
    }

    private fun resolveReminders(args: List<dto.ReminderArg>): ReminderResolve {
        val out = mutableListOf<TrackReminder>()
        for ((i, r) in args.withIndex()) {
            val offset = when {
                r.offsetMinutes != null -> r.offsetMinutes
                r.time != null -> parseHhmm(r.time) ?: return ReminderResolve.Fail("reminders[$i].time must be \"HH:MM\" (hours 0–47)")
                else -> return ReminderResolve.Fail("reminders[$i] needs a 'time' or 'offsetMinutes'")
            }
            if (offset < 0 || offset >= 48 * 60)
                return ReminderResolve.Fail("reminders[$i] offset must be within 0..2879 minutes")
            if (out.any { it.offsetMinutes == offset })
                return ReminderResolve.Fail("reminders[$i] duplicates an earlier time")
            val days = r.days.distinct().sorted()
            if (days.any { it !in 1..7 }) return ReminderResolve.Fail("reminders[$i].days must be ISO weekdays 1..7")
            out += TrackReminder(offsetMinutes = offset, days = days)
        }
        return ReminderResolve.Ok(out)
    }

    private fun parseHhmm(text: String): Int? {
        val parts = text.trim().split(":")
        if (parts.size != 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        if (h !in 0..47 || m !in 0..59) return null
        return h * 60 + m
    }

    private fun buildSchema(): ToolSchema {
        val props = buildJsonObject {
            putJsonObject("name") { put("type", "string"); put("description", "Track name (non-blank).") }
            putJsonObject("type") {
                put("type", "string")
                putJsonArray("enum") { add("check"); add("quantity"); add("timer") }
                put("description", "check (scheduled done/skip), quantity (numeric/text fields), or timer (elapsed time).")
            }
            putJsonObject("logOnly") { put("type", "boolean"); put("description", "Journal-only mode: no targets/streaks/stats.") }
            putJsonObject("unit") { put("type", "string"); put("description", "Single-field quantity/timer: measurement unit.") }
            putJsonObject("dailyTarget") { put("type", "number"); put("description", "Single-field quantity/timer: daily target (≥0; timer in seconds).") }
            putJsonObject("direction") { put("type", "string"); put("description", "Single-field quantity/timer: \"more\" or \"less\".") }
            putJsonObject("params") {
                put("type", "array")
                put("description", "Fields for a quantity track, or before/after annotation fields for a timer.")
                putJsonObject("items") {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("name") { put("type", "string"); put("description", "Field name.") }
                        putJsonObject("paramType") {
                            put("type", "string")
                            putJsonArray("enum") { add("number"); add("text") }
                            put("description", "number (default) or text.")
                        }
                        putJsonObject("unit") { put("type", "string"); put("description", "Number fields: measurement unit.") }
                        putJsonObject("dailyTarget") { put("type", "number"); put("description", "Number fields: daily target (≥0).") }
                        putJsonObject("direction") { put("type", "string"); put("description", "Number fields: \"more\" or \"less\".") }
                        putJsonObject("timerPhase") {
                            put("type", "string")
                            putJsonArray("enum") { add("before"); add("after") }
                            put("description", "Timer annotation fields only: collected before start or after stop.")
                        }
                    }
                }
            }
            putJsonObject("reminders") {
                put("type", "array")
                put("description", "Schedule (check tracks). Timer tracks take none.")
                putJsonObject("items") {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("time") { put("type", "string"); put("description", "\"HH:MM\", hours 0–47 (24+ = next day).") }
                        putJsonObject("offsetMinutes") { put("type", "integer"); put("description", "Minutes from midnight (0–2879); alternative to time.") }
                        putJsonObject("days") {
                            put("type", "array")
                            put("description", "ISO weekdays 1=Mon..7=Sun; empty = every day.")
                            putJsonObject("items") { put("type", "integer") }
                        }
                    }
                }
            }
        }
        return ToolSchema(properties = props, required = listOf("name", "type"))
    }
}
