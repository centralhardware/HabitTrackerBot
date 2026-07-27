package mcp

import BotNotifier
import Lang
import Strings
import dto.Direction
import dto.ParamType
import dto.TrackParam
import dto.TrackParamPatch
import dto.TrackReminder
import dto.TrackType
import dto.TrackUpdateArgs
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

object TrackUpdateTool : TypedMcpTool<TrackUpdateArgs>(TrackUpdateArgs.serializer()) {
    override val name = "track_update"
    override val description =
        "Edit a track's settings (not its check-ins). 'trackId' comes from tracks_list. Top-level fields edit the " +
            "track: 'name'; 'logOnly' (true = journal only, no targets/streaks/stats); and, " +
            "for a single-field quantity/timer track, 'dailyTarget'/'unit'/'direction' (\"more\"/\"less\"). " +
            "To edit the fields of a multi-field track, pass 'params': [{ paramId, name, unit, dailyTarget, direction }] " +
            "(paramId from tracks_list params[].id). Any of those scalars has a matching clearX flag (clearUnit, " +
            "clearDailyTarget, clearDirection, clearName) to set it empty. unit/dailyTarget/direction apply to number " +
            "fields only. 'reminders' replaces the whole schedule: omit to leave it untouched, pass [] to clear all, or " +
            "give the full new list — each slot a time \"HH:MM\" (hours 24–47 = next day) or raw 'offsetMinutes', plus " +
            "optional 'days' (ISO 1=Mon..7=Sun; empty = every day); timer tracks take no reminders. Provide at least one " +
            "change. (Removing a field is track_param_delete; pausing/deleting a track is track_lifecycle.)"
    override val inputSchema: ToolSchema = buildSchema()
    override val annotations = ToolAnnotations(
        readOnlyHint = false,
        destructiveHint = false,
        idempotentHint = true,
        openWorldHint = false,
    )

    override fun handle(userId: Long, lang: Lang, tz: ZoneId, args: TrackUpdateArgs): CallToolResult {
        val track = TrackService.findById(args.trackId, userId)
            ?: return err("Track ${args.trackId} not found")

        var updated = track
        val lines = mutableListOf<String>()

        args.name?.trim()?.let { newName ->
            if (newName.isEmpty()) return err("'name' must be non-blank")
            if (newName != track.name) {
                updated = updated.copy(name = newName)
                lines += "name → «$newName»"
            }
        }

        if (args.logOnly != null && args.logOnly != track.logOnly) {
            updated = updated.copy(logOnly = args.logOnly)
            lines += "logOnly → ${args.logOnly}"
        }

        // unit / dailyTarget / direction live on the track row only for single-field quantity/timer tracks.
        val metricEditable = track.type != TrackType.CHECK && !track.multiField
        val touchesMetric = args.unit != null || args.clearUnit ||
            args.dailyTarget != null || args.clearDailyTarget ||
            args.direction != null || args.clearDirection
        if (touchesMetric) {
            if (!metricEditable) return err(
                if (track.multiField) "This track has multiple fields — edit unit/dailyTarget/direction via 'params'."
                else "unit/dailyTarget/direction are not editable on a check track."
            )
            if (args.clearUnit || args.unit != null) {
                val newUnit = if (args.clearUnit) null else args.unit!!.trim().ifEmpty { null }
                updated = updated.copy(unit = newUnit)
                lines += "unit → ${newUnit ?: "∅"}"
            }
            if (args.clearDailyTarget || args.dailyTarget != null) {
                val newTarget = if (args.clearDailyTarget) null else args.dailyTarget
                if (newTarget != null && (newTarget < 0 || newTarget.isNaN() || newTarget.isInfinite()))
                    return err("'dailyTarget' must be ≥ 0 and finite")
                updated = updated.copy(dailyTarget = newTarget)
                lines += "dailyTarget → ${newTarget ?: "∅"}"
            }
            if (args.clearDirection || args.direction != null) {
                val newDir = if (args.clearDirection) null else Direction.parse(args.direction)
                    ?: return err("'direction' must be \"more\" or \"less\"")
                updated = updated.copy(direction = newDir)
                lines += "direction → ${newDir?.value ?: "∅"}"
            }
        }

        // Resolve the schedule (null = untouched). Validate up front so a bad slot fails before any write.
        var newReminders: List<TrackReminder>? = null
        if (args.reminders != null) {
            if (track.type == TrackType.TIMER && args.reminders.isNotEmpty())
                return err("Timer tracks don't use reminders")
            when (val res = resolveReminders(args.reminders)) {
                is ReminderResolve.Fail -> return err(res.msg)
                is ReminderResolve.Ok -> newReminders = res.reminders
            }
        }

        // Check-track invariant on the *final* state: a check track is scheduled-only, so it must
        // keep at least one reminder.
        if (track.type == TrackType.CHECK) {
            val finalReminders = newReminders ?: track.reminders
            if (finalReminders.isEmpty())
                return err("A check track must keep at least one reminder")
        }

        // Resolve per-field edits up front so a bad patch fails before anything is written.
        val paramUpdates = mutableListOf<Pair<TrackParam, List<String>>>()
        if (args.params.isNotEmpty()) {
            if (!track.multiField)
                return err("'params' edits are for multi-field tracks; this track's metric is on the track itself")
            for ((i, patch) in args.params.withIndex()) {
                val param = track.params.firstOrNull { it.id == patch.paramId }
                    ?: return err("params[$i].paramId ${patch.paramId} is not a field of track ${track.id}")
                when (val res = resolveParam(param, patch)) {
                    is ParamResolve.Fail -> return err("params[$i]: ${res.msg}")
                    is ParamResolve.Ok -> if (res.lines.isNotEmpty()) paramUpdates += res.param to res.lines
                }
            }
        }

        if (lines.isEmpty() && paramUpdates.isEmpty() && newReminders == null)
            return err("No changes: provide at least one different field to update")

        if (updated != track) TrackService.update(updated)
        for ((p, plines) in paramUpdates) {
            if (!TrackService.updateParam(p, userId)) return err("Field ${p.id} could not be updated")
            val label = p.name ?: "#${p.id}"
            plines.forEach { lines += "$label: $it" }
        }
        if (newReminders != null) {
            if (!TrackService.replaceReminders(track.id, userId, newReminders))
                return err("Track ${track.id} reminders could not be updated")
            lines += if (newReminders.isEmpty()) "reminders cleared"
            else "reminders → " + newReminders.sortedBy { it.offsetMinutes }.joinToString(", ") { rem ->
                val d = if (rem.days.isNotEmpty()) " (${rem.days.joinToString(",")})" else ""
                Strings.formatDisplayTime(rem.offsetMinutes) + d
            }
        }

        BotNotifier.notify(userId, Strings.mcpTrackUpdated(lang, updated.name, lines))
        return ok("""{"updated":true,"trackId":${track.id}}""")
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

    /** Parses "HH:MM" with hours 0..47 (24+ = next-day slot) into minutes from midnight. */
    private fun parseHhmm(text: String): Int? {
        val parts = text.trim().split(":")
        if (parts.size != 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        if (h !in 0..47 || m !in 0..59) return null
        return h * 60 + m
    }

    private sealed interface ParamResolve {
        data class Ok(val param: TrackParam, val lines: List<String>) : ParamResolve
        data class Fail(val msg: String) : ParamResolve
    }

    private fun resolveParam(param: TrackParam, patch: TrackParamPatch): ParamResolve {
        var p = param
        val lines = mutableListOf<String>()
        if (patch.clearName || patch.name != null) {
            val newName = if (patch.clearName) null else patch.name!!.trim().ifEmpty { null }
            if (newName != param.name) { p = p.copy(name = newName); lines += "name → ${newName ?: "∅"}" }
        }
        val touchesMetric = patch.unit != null || patch.clearUnit ||
            patch.dailyTarget != null || patch.clearDailyTarget ||
            patch.direction != null || patch.clearDirection
        if (touchesMetric) {
            if (param.paramType != ParamType.NUMBER)
                return ParamResolve.Fail("unit/dailyTarget/direction apply only to number fields; this field is text")
            if (patch.clearUnit || patch.unit != null) {
                val newUnit = if (patch.clearUnit) null else patch.unit!!.trim().ifEmpty { null }
                p = p.copy(unit = newUnit); lines += "unit → ${newUnit ?: "∅"}"
            }
            if (patch.clearDailyTarget || patch.dailyTarget != null) {
                val newTarget = if (patch.clearDailyTarget) null else patch.dailyTarget
                if (newTarget != null && (newTarget < 0 || newTarget.isNaN() || newTarget.isInfinite()))
                    return ParamResolve.Fail("'dailyTarget' must be ≥ 0 and finite")
                p = p.copy(dailyTarget = newTarget); lines += "dailyTarget → ${newTarget ?: "∅"}"
            }
            if (patch.clearDirection || patch.direction != null) {
                val newDir = if (patch.clearDirection) null else Direction.parse(patch.direction)
                    ?: return ParamResolve.Fail("'direction' must be \"more\" or \"less\"")
                p = p.copy(direction = newDir); lines += "direction → ${newDir?.value ?: "∅"}"
            }
        }
        return ParamResolve.Ok(p, lines)
    }

    private fun buildSchema(): ToolSchema {
        val props = buildJsonObject {
            putJsonObject("trackId") { put("type", "integer"); put("description", "The track's id (from tracks_list).") }
            putJsonObject("name") { put("type", "string"); put("description", "New track name (non-blank).") }
            putJsonObject("logOnly") { put("type", "boolean"); put("description", "Journal-only mode: no targets/streaks/stats.") }
            putJsonObject("dailyTarget") { put("type", "number"); put("description", "Single-field quantity/timer only: daily target (≥0).") }
            putJsonObject("clearDailyTarget") { put("type", "boolean"); put("description", "Set true to remove the daily target.") }
            putJsonObject("unit") { put("type", "string"); put("description", "Single-field quantity/timer only: measurement unit.") }
            putJsonObject("clearUnit") { put("type", "boolean"); put("description", "Set true to remove the unit.") }
            putJsonObject("direction") { put("type", "string"); put("description", "Single-field quantity/timer only: \"more\" or \"less\".") }
            putJsonObject("clearDirection") { put("type", "boolean"); put("description", "Set true to remove the direction.") }
            putJsonObject("params") {
                put("type", "array")
                put("description", "Per-field edits for a multi-field track. Only listed fields change.")
                putJsonObject("items") {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("paramId") { put("type", "integer"); put("description", "Field id from tracks_list params[].id.") }
                        putJsonObject("name") { put("type", "string"); put("description", "New field name.") }
                        putJsonObject("clearName") { put("type", "boolean"); put("description", "Set true to clear the field name.") }
                        putJsonObject("unit") { put("type", "string"); put("description", "Number fields only: measurement unit.") }
                        putJsonObject("clearUnit") { put("type", "boolean"); put("description", "Set true to remove the unit.") }
                        putJsonObject("dailyTarget") { put("type", "number"); put("description", "Number fields only: daily target (≥0).") }
                        putJsonObject("clearDailyTarget") { put("type", "boolean"); put("description", "Set true to remove the daily target.") }
                        putJsonObject("direction") { put("type", "string"); put("description", "Number fields only: \"more\" or \"less\".") }
                        putJsonObject("clearDirection") { put("type", "boolean"); put("description", "Set true to remove the direction.") }
                    }
                    putJsonArray("required") { add("paramId") }
                }
            }
            putJsonObject("reminders") {
                put("type", "array")
                put("description", "Full replacement schedule. Omit to leave untouched; [] clears all. Timer tracks take none.")
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
        return ToolSchema(properties = props, required = listOf("trackId"))
    }
}
