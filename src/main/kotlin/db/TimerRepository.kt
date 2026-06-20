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

    /**
     * Starts a timer for [habitId]; false if one is already running (PK conflict). [pendingValuesJson]
     * is the JSON object of "before"-phase field values to carry until the timer stops (null when none).
     */
    fun start(habitId: Long, userId: Long, pendingValuesJson: String?): Boolean =
        using(sessionOf(DatabaseService.dataSource)) { session ->
            session.update(
                queryOf(
                    """
                    INSERT INTO running_timers (habit_id, user_id, started_at, pending_values)
                    VALUES (?, ?, now(), ?::jsonb)
                    ON CONFLICT (habit_id) DO NOTHING
                    """.trimIndent(),
                    habitId, userId, pendingValuesJson
                )
            ) > 0
        }

    /** Result of stopping a timer: the final live-segment start, the time banked from earlier
     * segments, whether it was paused at stop, and the stashed "before"-phase values JSON. */
    data class StopRow(val startedAt: Instant, val accumulatedSeconds: Double, val paused: Boolean, val pendingValuesJson: String?)

    /**
     * Stops the timer for [habitId], returning its accumulated/started state and the JSON of its
     * stashed "before"-phase field values (or null), or null if none was running.
     */
    fun stop(habitId: Long, userId: Long): StopRow? =
        sessionOf(DatabaseService.dataSource).use { session ->
            session.run(
                queryOf(
                    """
                    WITH stopped AS (
                        DELETE FROM running_timers
                        WHERE habit_id = ? AND user_id = ?
                        RETURNING started_at, accumulated_seconds, paused_at, pending_values
                    )
                    SELECT started_at, accumulated_seconds, paused_at, pending_values FROM stopped
                    """.trimIndent(),
                    habitId, userId
                ).map {
                    StopRow(
                        it.instant("started_at"),
                        it.double("accumulated_seconds"),
                        it.instantOrNull("paused_at") != null,
                        it.stringOrNull("pending_values"),
                    )
                }.asSingle
            )
        }

    /**
     * Pauses a running timer: banks the live segment into accumulated_seconds (kept only for the
     * live session display) and marks it paused. Returns the `started_at` of the just-ended live
     * segment so the caller can record it as a check-in, or null if it wasn't running / already paused.
     */
    fun pause(habitId: Long, userId: Long): Instant? =
        sessionOf(DatabaseService.dataSource).use { session ->
            session.run(
                queryOf(
                    """
                    UPDATE running_timers
                    SET accumulated_seconds = accumulated_seconds + EXTRACT(EPOCH FROM (now() - started_at)),
                        paused_at = now()
                    WHERE habit_id = ? AND user_id = ? AND paused_at IS NULL
                    RETURNING started_at
                    """.trimIndent(),
                    habitId, userId
                ).map { it.instant("started_at") }.asSingle
            )
        }

    /**
     * Resumes a paused timer: restarts the live segment from now and clears the paused marker.
     * No-op (returns false) if the timer is missing or not paused.
     */
    fun resume(habitId: Long, userId: Long): Boolean =
        using(sessionOf(DatabaseService.dataSource)) { session ->
            session.update(
                queryOf(
                    """
                    UPDATE running_timers
                    SET started_at = now(), paused_at = NULL
                    WHERE habit_id = ? AND user_id = ? AND paused_at IS NOT NULL
                    """.trimIndent(),
                    habitId, userId
                )
            ) > 0
        }

    fun find(habitId: Long, userId: Long): RunningTimer? =
        sessionOf(DatabaseService.dataSource).use { session ->
            session.run(
                queryOf(
                    "SELECT habit_id, user_id, started_at, accumulated_seconds, paused_at FROM running_timers WHERE habit_id = ? AND user_id = ?",
                    habitId, userId
                ).map { it.toRunningTimer() }.asSingle
            )
        }

    /** All running timers owned by [userId]. */
    fun running(userId: Long): List<RunningTimer> =
        sessionOf(DatabaseService.dataSource).use { session ->
            session.run(
                queryOf(
                    "SELECT habit_id, user_id, started_at, accumulated_seconds, paused_at FROM running_timers WHERE user_id = ?",
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
                    SELECT rt.habit_id, rt.user_id, rt.started_at, rt.accumulated_seconds, rt.paused_at, rt.message_id,
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
