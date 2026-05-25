package db

import DatabaseService
import dto.Checkin
import dto.CheckinRecord
import dto.DayAmount
import dto.DayCount
import dto.DayStatus
import dto.PendingCheckIn
import dto.ScheduledTotals
import dto.toCheckinRecord
import dto.toDayAmount
import dto.toDayCount
import dto.toDayStatus
import dto.toPendingCheckIn
import dto.toScheduledTotals
import kotliquery.queryOf
import kotliquery.sessionOf
import java.time.Instant
import java.time.LocalDate

object CheckInRepository {

    fun upsert(checkin: Checkin): Boolean {
        return sessionOf(DatabaseService.dataSource).use { session ->
            session.update(
                queryOf(
                    """
                    INSERT INTO checkins (habit_id, reminder_id, check_date, status, quantity, comment)
                    VALUES (?, ?, ?, ?, ?, ?)
                    ON CONFLICT (reminder_id, check_date) WHERE reminder_id IS NOT NULL DO UPDATE
                        SET status = EXCLUDED.status,
                            checked_at = now()
                    """.trimIndent(),
                    checkin.habitId,
                    checkin.reminderId,
                    checkin.checkDate,
                    checkin.status?.value,
                    checkin.quantity,
                    checkin.comment,
                )
            ) > 0
        }
    }

    fun markPendingAsSkip(threshold: Instant): Int {
        return sessionOf(DatabaseService.dataSource).use { session ->
            session.update(
                queryOf(
                    """
                    UPDATE checkins
                    SET status = 'skip', checked_at = now()
                    WHERE status IS NULL
                      AND reminder_id IS NOT NULL
                      AND checked_at < ?
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
                    FROM checkins
                    WHERE habit_id = ? AND reminder_id IS NULL AND check_date = ?
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
                    SELECT COALESCE(SUM(quantity), 0) AS total
                    FROM checkins
                    WHERE habit_id = ? AND reminder_id IS NULL AND check_date = ?
                    """.trimIndent(),
                    habitId, date
                ).map { it.double("total") }.asSingle
            ) ?: 0.0
        }
    }

    fun counterCountsPerDay(habitId: Long): List<DayCount> {
        return sessionOf(DatabaseService.dataSource).use { session ->
            session.run(
                queryOf(
                    """
                    SELECT check_date, COUNT(*) AS cnt
                    FROM checkins
                    WHERE habit_id = ? AND reminder_id IS NULL
                    GROUP BY check_date
                    ORDER BY check_date
                    """.trimIndent(),
                    habitId
                ).map { it.toDayCount() }.asList
            )
        }
    }

    fun quantitySumsPerDay(habitId: Long): List<DayAmount> {
        return sessionOf(DatabaseService.dataSource).use { session ->
            session.run(
                queryOf(
                    """
                    SELECT check_date, SUM(quantity)::float AS amt
                    FROM checkins
                    WHERE habit_id = ? AND reminder_id IS NULL AND quantity IS NOT NULL
                    GROUP BY check_date
                    ORDER BY check_date
                    """.trimIndent(),
                    habitId
                ).map { it.toDayAmount() }.asList
            )
        }
    }

    fun habitStartDate(habitId: Long): LocalDate? {
        return sessionOf(DatabaseService.dataSource).use { session ->
            session.run(
                queryOf(
                    """
                    SELECT (h.created_at AT TIME ZONE us.timezone)::date AS start_d
                    FROM habits h
                    JOIN user_settings us ON us.user_id = h.user_id
                    WHERE h.id = ?
                    """.trimIndent(),
                    habitId
                ).map { it.localDate("start_d") }.asSingle
            )
        }
    }

    fun scheduledTotals(habitId: Long): ScheduledTotals {
        return sessionOf(DatabaseService.dataSource).use { session ->
            session.run(
                queryOf(
                    """
                    SELECT COUNT(DISTINCT c.check_date)                       AS total_days,
                           COUNT(*) FILTER (WHERE c.status = 'done')          AS done_count,
                           COUNT(*) FILTER (WHERE c.status = 'skip')          AS skip_count
                    FROM checkins c
                    WHERE c.habit_id = ? AND c.reminder_id IS NOT NULL
                    """.trimIndent(),
                    habitId
                ).map { it.toScheduledTotals() }.asSingle
            ) ?: ScheduledTotals(0, 0, 0)
        }
    }

    fun findDailyDoneStatus(habitId: Long): List<DayStatus> {
        return sessionOf(DatabaseService.dataSource).use { session ->
            session.run(
                queryOf(
                    """
                    SELECT c.check_date,
                           COUNT(*) = COUNT(*) FILTER (WHERE c.status = 'done') AS day_done
                    FROM checkins c
                    WHERE c.habit_id = ? AND c.reminder_id IS NOT NULL
                    GROUP BY c.check_date
                    ORDER BY c.check_date DESC
                    """.trimIndent(),
                    habitId
                ).map { it.toDayStatus() }.asList
            )
        }
    }

    fun findInRange(habitId: Long, from: LocalDate, to: LocalDate): List<CheckinRecord> {
        return sessionOf(DatabaseService.dataSource).use { session ->
            session.run(
                queryOf(
                    """
                    SELECT c.check_date, c.status, c.quantity, c.comment, r.reminder_time
                    FROM checkins c
                    LEFT JOIN habit_reminders r ON r.id = c.reminder_id
                    WHERE c.habit_id = ?
                      AND c.check_date BETWEEN ?::date AND ?::date
                    ORDER BY c.check_date, r.reminder_time NULLS FIRST, c.id
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
                    SELECT c.reminder_id, h.name, r.reminder_time, c.check_date
                    FROM checkins c
                    JOIN habit_reminders r ON r.id = c.reminder_id
                    JOIN habits h ON h.id = c.habit_id
                    WHERE h.user_id = ?
                      AND c.status IS NULL
                      AND c.check_date BETWEEN ?::date AND ?::date
                    ORDER BY c.check_date, r.reminder_time
                    """.trimIndent(),
                    userId, fromDate, toDate
                ).map { it.toPendingCheckIn() }.asList
            )
        }
    }
}
