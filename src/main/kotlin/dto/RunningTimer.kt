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
