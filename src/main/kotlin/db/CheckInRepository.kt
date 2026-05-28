package db

import DatabaseService
import dto.CheckinEvent
import dto.CheckinRecord
import dto.CheckinValue
import dto.DayAmount
import dto.PendingCheckIn
import dto.toCheckinRecord
import dto.toDayAmount
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
     */
    fun upsertScheduledValue(event: CheckinEvent, value: CheckinValue): Boolean {
        require(event.reminderId != null) { "scheduled upsert requires reminderId" }
        return using(sessionOf(DatabaseService.dataSource, returnGeneratedKey = true)) { session ->
            session.transaction { tx ->
                val eventId = tx.run(
                    queryOf(
                        """
                        INSERT INTO checkins (user_id, check_date, reminder_id, comment, checked_at)
                        VALUES (?, ?, ?, NULL, now())
                        ON CONFLICT (reminder_id, check_date) WHERE reminder_id IS NOT NULL
                        DO UPDATE SET checked_at = now()
                        RETURNING id
                        """.trimIndent(),
                        event.userId, event.checkDate, event.reminderId
                    ).map { it.long("id") }.asSingle
                ) ?: return@transaction false

                tx.update(
                    queryOf(
                        """
                        INSERT INTO checkin_values (checkin_id, habit_id, status, quantity)
                        VALUES (?, ?, ?::checkin_status, ?)
                        ON CONFLICT (checkin_id, habit_id)
                        DO UPDATE SET status = EXCLUDED.status,
                                      quantity = EXCLUDED.quantity
                        """.trimIndent(),
                        eventId, value.habitId, value.status?.value, value.quantity
                    )
                )
                true
            }
        }
    }

    /**
     * Insert a non-scheduled event (counter/quantity) with one or more values.
     * Each call creates a fresh event row — there is no per-day uniqueness.
     */
    fun insertEventWithValues(event: CheckinEvent, values: List<CheckinValue>): Int {
        if (values.isEmpty()) return 0
        return using(sessionOf(DatabaseService.dataSource, returnGeneratedKey = true)) { session ->
            session.transaction { tx ->
                val eventId = tx.run(
                    queryOf(
                        """
                        INSERT INTO checkins (user_id, check_date, reminder_id, comment, checked_at)
                        VALUES (?, ?, NULL, ?, now())
                        RETURNING id
                        """.trimIndent(),
                        event.userId, event.checkDate, event.comment
                    ).map { it.long("id") }.asSingle
                ) ?: error("Failed to insert checkin event")

                var wrote = 0
                values.forEach { v ->
                    wrote += tx.update(
                        queryOf(
                            """
                            INSERT INTO checkin_values (checkin_id, habit_id, status, quantity)
                            VALUES (?, ?, ?::checkin_status, ?)
                            """.trimIndent(),
                            eventId, v.habitId, v.status?.value, v.quantity
                        )
                    )
                }
                wrote
            }
        }
    }

    fun markPendingAsSkip(threshold: Instant): Int {
        return sessionOf(DatabaseService.dataSource).use { session ->
            session.update(
                queryOf(
                    """
                    UPDATE checkin_values v
                    SET status = 'skip'
                    FROM checkins e
                    WHERE v.checkin_id = e.id
                      AND v.status IS NULL
                      AND e.reminder_id IS NOT NULL
                      AND e.checked_at < ?
                    """.trimIndent(),
                    threshold
                )
            )
        }
    }

    fun todayCounterCount(habitId: Long, date: LocalDate): Int {
        return sessionOf(DatabaseService.dataSource).use { session ->
            session.run(
                queryOf(
                    """
                    SELECT COUNT(*) AS cnt
                    FROM checkins e
                    JOIN checkin_values v ON v.checkin_id = e.id
                    WHERE v.habit_id = ? AND e.reminder_id IS NULL AND e.check_date = ?
                    """.trimIndent(),
                    habitId, date
                ).map { it.int("cnt") }.asSingle
            ) ?: 0
        }
    }

    fun todayQuantitySum(habitId: Long, date: LocalDate): Double {
        return sessionOf(DatabaseService.dataSource).use { session ->
            session.run(
                queryOf(
                    """
                    SELECT COALESCE(SUM(v.quantity), 0) AS total
                    FROM checkins e
                    JOIN checkin_values v ON v.checkin_id = e.id
                    WHERE v.habit_id = ? AND e.reminder_id IS NULL AND e.check_date = ?
                    """.trimIndent(),
                    habitId, date
                ).map { it.double("total") }.asSingle
            ) ?: 0.0
        }
    }

    fun firstCheckinDate(habitId: Long): LocalDate? {
        return sessionOf(DatabaseService.dataSource).use { session ->
            session.run(
                queryOf(
                    """
                    SELECT MIN(e.check_date) AS d
                    FROM checkins e
                    JOIN checkin_values v ON v.checkin_id = e.id
                    WHERE v.habit_id = ?
                    """.trimIndent(),
                    habitId
                ).map { it.localDateOrNull("d") }.asSingle
            )
        }
    }

    fun quantitySumsPerDay(habitId: Long): List<DayAmount> {
        return sessionOf(DatabaseService.dataSource).use { session ->
            session.run(
                queryOf(
                    """
                    SELECT e.check_date, SUM(v.quantity)::float AS amt
                    FROM checkins e
                    JOIN checkin_values v ON v.checkin_id = e.id
                    WHERE v.habit_id = ? AND e.reminder_id IS NULL AND v.quantity IS NOT NULL
                    GROUP BY e.check_date
                    ORDER BY e.check_date
                    """.trimIndent(),
                    habitId
                ).map { it.toDayAmount() }.asList
            )
        }
    }

    fun loggedDates(habitId: Long): List<LocalDate> {
        return sessionOf(DatabaseService.dataSource).use { session ->
            session.run(
                queryOf(
                    """
                    SELECT DISTINCT e.check_date
                    FROM checkins e
                    JOIN checkin_values v ON v.checkin_id = e.id
                    WHERE v.habit_id = ?
                      AND (e.reminder_id IS NULL OR v.status = 'done')
                    ORDER BY e.check_date
                    """.trimIndent(),
                    habitId
                ).map { it.localDate("check_date") }.asList
            )
        }
    }

    fun skipDates(habitId: Long): List<LocalDate> {
        return sessionOf(DatabaseService.dataSource).use { session ->
            session.run(
                queryOf(
                    """
                    SELECT DISTINCT e.check_date
                    FROM checkins e
                    JOIN checkin_values v ON v.checkin_id = e.id
                    WHERE v.habit_id = ? AND v.status = 'skip'
                    ORDER BY e.check_date
                    """.trimIndent(),
                    habitId
                ).map { it.localDate("check_date") }.asList
            )
        }
    }

    fun findInRange(habitId: Long, from: LocalDate, to: LocalDate): List<CheckinRecord> {
        return sessionOf(DatabaseService.dataSource).use { session ->
            session.run(
                queryOf(
                    """
                    SELECT e.check_date, v.status, v.quantity, e.comment, r.reminder_time
                    FROM checkins e
                    JOIN checkin_values v ON v.checkin_id = e.id
                    LEFT JOIN habit_reminders r ON r.id = e.reminder_id
                    WHERE v.habit_id = ?
                      AND e.check_date BETWEEN ?::date AND ?::date
                    ORDER BY e.check_date, r.reminder_time NULLS FIRST, e.id
                    """.trimIndent(),
                    habitId, from, to
                ).map { it.toCheckinRecord() }.asList
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
