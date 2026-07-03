package dto

import kotliquery.Row
import kotlinx.serialization.json.Json
import java.time.Instant

/** Decodes a `running_timers.pending_values` jsonb object (paramId → text) to a map, empty on null/garbage. */
private fun Row.beforeValues(column: String): Map<Long, String> =
    stringOrNull(column)?.let { runCatching { Json.decodeFromString<Map<Long, String>>(it) }.getOrNull() } ?: emptyMap()

/**
 * An in-flight timer: one row in `running_timers`, started but not yet stopped. [accumulatedSeconds]
 * is time banked from earlier run segments (before pauses); [pausedAt] is non-null while paused, in
 * which case the live segment is frozen and only [accumulatedSeconds] counts.
 */
data class RunningTimer(
    val trackId: Long,
    val userId: Long,
    val startedAt: Instant,
    val accumulatedSeconds: Double = 0.0,
    val pausedAt: Instant? = null,
    /** "before"-phase annotation field values stashed at start (paramId → text). */
    val beforeValues: Map<Long, String> = emptyMap(),
) {
    val paused: Boolean get() = pausedAt != null
}

fun Row.toRunningTimer(): RunningTimer = RunningTimer(
    trackId = long("track_id"),
    userId = long("user_id"),
    startedAt = instant("started_at"),
    accumulatedSeconds = double("accumulated_seconds"),
    pausedAt = instantOrNull("paused_at"),
    beforeValues = beforeValues("pending_values"),
)

/** A running timer plus everything the background ticker needs to repaint its live message. */
data class RunningTimerTick(
    val trackId: Long,
    val userId: Long,
    val name: String,
    val startedAt: Instant,
    val accumulatedSeconds: Double,
    val pausedAt: Instant?,
    val messageId: Long,
    val langCode: String?,
    val tzId: String?,
    /** "before"-phase annotation field values stashed at start (paramId → text). */
    val beforeValues: Map<Long, String> = emptyMap(),
) {
    val paused: Boolean get() = pausedAt != null
}

fun Row.toRunningTimerTick(): RunningTimerTick = RunningTimerTick(
    trackId = long("track_id"),
    userId = long("user_id"),
    name = string("name"),
    startedAt = instant("started_at"),
    accumulatedSeconds = double("accumulated_seconds"),
    pausedAt = instantOrNull("paused_at"),
    messageId = long("message_id"),
    langCode = stringOrNull("lang"),
    tzId = stringOrNull("tz"),
    beforeValues = beforeValues("pending_values"),
)
