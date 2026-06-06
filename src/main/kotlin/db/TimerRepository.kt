package db

import services.DatabaseService
import dto.RunningTimer
import dto.toRunningTimer
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
}
