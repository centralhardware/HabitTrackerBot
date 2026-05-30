package db

import services.DatabaseService
import dto.CheckinEvent
import dto.CheckinValue
import dto.CheckinValueRow
import dto.PendingCheckIn
import dto.toCheckinValueRow
import dto.toResolvedCheckin
import dto.toPendingCheckIn
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import java.time.Instant
import java.time.LocalDate

object CheckInRepository {

    /**
     * Upsert a scheduled-reminder check-in: keyed on (reminder_id, check_date).
     * Used for pending-row creation and for the done/skip click.
     *
     * Implemented as a single CTE so we avoid the `returnGeneratedKey` +
     * explicit `RETURNING` + `asSingle` combo that crashes PG JDBC 42.7.4
     * with "No results were returned by the query."
     */
    fun upsertScheduledValue(event: CheckinEvent, value: CheckinValue): Boolean {
        require(event.reminderId != null) { "scheduled upsert requires reminderId" }
        return using(sessionOf(DatabaseService.dataSource)) { session ->
            val written = session.update(
                queryOf(
                    """
                    WITH upsert_event AS (
                        INSERT INTO checkins (user_id, check_date, reminder_id, comment, checked_at)
                        VALUES (?, ?, ?, NULL, now())
                        ON CONFLICT (reminder_id, check_date)
                            WHERE reminder_id IS NOT NULL
                        DO UPDATE SET checked_at = now()
                        RETURNING id
                    )
                    INSERT INTO checkin_values (checkin_id, habit_id, status, quantity)
                    SELECT id, ?, ?::checkin_status, ?
                    FROM upsert_event
                    ON CONFLICT (checkin_id, habit_id)
                    DO UPDATE SET status = EXCLUDED.status,
                                  quantity = EXCLUDED.quantity
                    """.trimIndent(),
                    event.userId, event.checkDate, event.reminderId,
                    value.habitId, value.status?.value, value.quantity
                )
            )
            written > 0
        }
    }

    /**
     * Insert a non-scheduled event (counter/quantity) with one or more values.
     * Each call creates a fresh event row — there is no per-day uniqueness.
     * The event id is pulled into the values via lastval() within the same
     * transaction, avoiding a RETURNING round-trip.
     */
    fun insertEventWithValues(event: CheckinEvent, values: List<CheckinValue>): Int {
        if (values.isEmpty()) return 0
        return using(sessionOf(DatabaseService.dataSource)) { session ->
            session.transaction { tx ->
                tx.update(
                    queryOf(
                        """
                        INSERT INTO checkins (user_id, check_date, reminder_id, comment, checked_at)
                        VALUES (?, ?, NULL, ?, now())
                        """.trimIndent(),
                        event.userId, event.checkDate, event.comment
                    )
                )
                var wrote = 0
                values.forEach { v ->
                    wrote += tx.update(
                        queryOf(
                            """
                            INSERT INTO checkin_values (checkin_id, habit_id, status, quantity)
                            VALUES (lastval(), ?, ?::checkin_status, ?)
                            """.trimIndent(),
                            v.habitId, v.status?.value, v.quantity
                        )
                    )
                }
                wrote
            }
        }
    }

    /** Skips overdue pending scheduled values, returning the (reminder, date) of each flipped row. */
    fun markPendingAsSkip(threshold: Instant): List<dto.ResolvedCheckin> {
        return sessionOf(DatabaseService.dataSource).use { session ->
            session.run(
                queryOf(
                    """
                    UPDATE checkin_values v
                    SET status = 'skip'
                    FROM checkins e
                    WHERE v.checkin_id = e.id
                      AND v.status IS NULL
                      AND e.reminder_id IS NOT NULL
                      AND e.checked_at < ?
                    RETURNING e.reminder_id AS reminder_id, e.check_date AS check_date
                    """.trimIndent(),
                    threshold
                ).map { it.toResolvedCheckin() }.asList
            )
        }
    }

    /**
     * Loads the full check-in history of a single habit as raw rows. All per-habit
     * stats (counts, sums, streaks, weekly totals) are computed over this list in
     * Kotlin (see [CheckinAnalytics]) instead of via specialized aggregate queries.
     */
    fun loadForHabit(habitId: Long): List<CheckinValueRow> {
        return sessionOf(DatabaseService.dataSource).use { session ->
            session.run(
                queryOf(
                    """
                    SELECT e.check_date, e.reminder_id, v.status, v.quantity, e.comment, r.reminder_time
                    FROM checkins e
                    JOIN checkin_values v ON v.checkin_id = e.id
                    LEFT JOIN habit_reminders r ON r.id = e.reminder_id
                    WHERE v.habit_id = ?
                    ORDER BY e.check_date, r.reminder_time NULLS FIRST, e.id
                    """.trimIndent(),
                    habitId
                ).map { it.toCheckinValueRow() }.asList
            )
        }
    }

    fun pendingCheckIns(userId: Long, fromDate: LocalDate, toDate: LocalDate): List<PendingCheckIn> {
        return sessionOf(DatabaseService.dataSource).use { session ->
            session.run(
                queryOf(
                    """
                    SELECT e.reminder_id, h.name, r.reminder_time, e.check_date
                    FROM checkins e
                    JOIN checkin_values v ON v.checkin_id = e.id
                    JOIN habit_reminders r ON r.id = e.reminder_id
                    JOIN habits h ON h.id = v.habit_id
                    WHERE h.user_id = ?
                      AND v.status IS NULL
                      AND e.check_date BETWEEN ?::date AND ?::date
                    ORDER BY e.check_date, r.reminder_time
                    """.trimIndent(),
                    userId, fromDate, toDate
                ).map { it.toPendingCheckIn() }.asList
            )
        }
    }
}
