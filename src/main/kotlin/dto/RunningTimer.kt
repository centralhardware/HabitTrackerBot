package dto

import kotliquery.Row
import java.time.Instant

/** An in-flight timer: one row in `running_timers`, started but not yet stopped. */
data class RunningTimer(
    val habitId: Long,
    val userId: Long,
    val startedAt: Instant,
)

fun Row.toRunningTimer(): RunningTimer = RunningTimer(
    habitId = long("habit_id"),
    userId = long("user_id"),
    startedAt = instant("started_at"),
)

/** A running timer plus everything the background ticker needs to repaint its live message. */
data class RunningTimerTick(
    val habitId: Long,
    val userId: Long,
    val name: String,
    val startedAt: Instant,
    val messageId: Long,
    val langCode: String?,
    val tzId: String?,
)

fun Row.toRunningTimerTick(): RunningTimerTick = RunningTimerTick(
    habitId = long("habit_id"),
    userId = long("user_id"),
    name = string("name"),
    startedAt = instant("started_at"),
    messageId = long("message_id"),
    langCode = stringOrNull("lang"),
    tzId = stringOrNull("tz"),
)
