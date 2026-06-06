package db

import services.DatabaseService
import dto.RunningTimer
import dto.RunningTimerTick
import dto.toRunningTimer
import dto.toRunningTimerTick
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import java.time.Instant

object TimerRepository {

    /** Starts a timer for [habitId]; false if one is already running (PK conflict). */
    fun start(habitId: Long, userId: Long): Boolean =
        using(sessionOf(DatabaseService.dataSource)) { session ->
            session.update(
                queryOf(
                    """
                    INSERT INTO running_timers (habit_id, user_id, started_at)
                    VALUES (?, ?, now())
                    ON CONFLICT (habit_id) DO NOTHING
                    """.trimIndent(),
                    habitId, userId
                )
            ) > 0
        }

    /** Stops the timer for [habitId], returning the moment it was started, or null if none was running. */
    fun stop(habitId: Long, userId: Long): Instant? =
        sessionOf(DatabaseService.dataSource).use { session ->
            session.run(
                queryOf(
                    """
                    WITH stopped AS (
                        DELETE FROM running_timers
                        WHERE habit_id = ? AND user_id = ?
                        RETURNING started_at
                    )
                    SELECT started_at FROM stopped
                    """.trimIndent(),
                    habitId, userId
                ).map { it.instant("started_at") }.asSingle
            )
        }

    fun find(habitId: Long, userId: Long): RunningTimer? =
        sessionOf(DatabaseService.dataSource).use { session ->
            session.run(
                queryOf(
                    "SELECT habit_id, user_id, started_at FROM running_timers WHERE habit_id = ? AND user_id = ?",
                    habitId, userId
                ).map { it.toRunningTimer() }.asSingle
            )
        }

    /** All running timers owned by [userId]. */
    fun running(userId: Long): List<RunningTimer> =
        sessionOf(DatabaseService.dataSource).use { session ->
            session.run(
                queryOf(
                    "SELECT habit_id, user_id, started_at FROM running_timers WHERE user_id = ?",
                    userId
                ).map { it.toRunningTimer() }.asList
            )
        }

    /** Records which message currently displays the running timer, so the ticker can edit it. */
    fun setMessage(habitId: Long, userId: Long, messageId: Long): Boolean =
        using(sessionOf(DatabaseService.dataSource)) { session ->
            session.update(
                queryOf(
                    "UPDATE running_timers SET message_id = ? WHERE habit_id = ? AND user_id = ?",
                    messageId, habitId, userId
                )
            ) > 0
        }

    /** Every running timer that has a tracked message, with the data needed to repaint it live. */
    fun dueTicks(): List<RunningTimerTick> =
        sessionOf(DatabaseService.dataSource).use { session ->
            session.run(
                queryOf(
                    """
                    SELECT rt.habit_id, rt.user_id, rt.started_at, rt.message_id,
                           h.name, us.language AS lang, us.timezone AS tz
                    FROM running_timers rt
                    JOIN habits h ON h.id = rt.habit_id
                    LEFT JOIN user_settings us ON us.user_id = rt.user_id
                    WHERE rt.message_id IS NOT NULL
                      AND h.status = 'active'
                    """.trimIndent()
                ).map { it.toRunningTimerTick() }.asList
            )
        }
}
