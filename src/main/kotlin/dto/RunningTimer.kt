package dto

import kotliquery.Row
import java.time.Instant

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
) {
    val paused: Boolean get() = pausedAt != null
}

fun Row.toRunningTimer(): RunningTimer = RunningTimer(
    trackId = long("track_id"),
    userId = long("user_id"),
    startedAt = instant("started_at"),
    accumulatedSeconds = double("accumulated_seconds"),
    pausedAt = instantOrNull("paused_at"),
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
)
