package db

import DatabaseService
import dto.Checkin
import dto.CheckinRecord
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

    fun upsert(checkin: Checkin): Boolean {
        return sessionOf(DatabaseService.dataSource).use { session ->
            session.update(
                queryOf(
                    """
                    INSERT INTO checkins (habit_id, reminder_id, check_date, status, quantity, comment_id)
                    VALUES (?, ?, ?, ?::checkin_status, ?, ?)
                    ON CONFLICT (reminder_id, check_date) WHERE reminder_id IS NOT NULL DO UPDATE
                        SET status = EXCLUDED.status,
                            checked_at = now()
                    """.trimIndent(),
                    checkin.habitId,
                    checkin.reminderId,
                    checkin.checkDate,
                    checkin.status?.value,
                    checkin.quantity,
                    checkin.commentId,
                )
            ) > 0
        }
    }

    fun createComment(body: String?): Long {
        return using(sessionOf(DatabaseService.dataSource, returnGeneratedKey = true)) { session ->
            session.updateAndReturnGeneratedKey(
                queryOf("INSERT INTO comments (body) VALUES (?)", body)
            ) ?: error("Failed to create comment")
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

    fun firstCheckinDate(habitId: Long): LocalDate? {
        return sessionOf(DatabaseService.dataSource).use { session ->
            session.run(
                queryOf(
                    "SELECT MIN(check_date) AS d FROM checkins WHERE habit_id = ?",
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

    fun loggedDates(habitId: Long): List<LocalDate> {
        return sessionOf(DatabaseService.dataSource).use { session ->
            session.run(
                queryOf(
                    """
                    SELECT DISTINCT c.check_date
                    FROM checkins c
                    WHERE c.habit_id = ?
                      AND (c.reminder_id IS NULL OR c.status = 'done')
                    ORDER BY c.check_date
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
                    SELECT DISTINCT c.check_date
                    FROM checkins c
                    WHERE c.habit_id = ? AND c.status = 'skip'
                    ORDER BY c.check_date
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
                    SELECT c.check_date, c.status, c.quantity, cm.body AS comment, r.reminder_time
                    FROM checkins c
                    LEFT JOIN habit_reminders r ON r.id = c.reminder_id
                    LEFT JOIN comments cm ON cm.id = c.comment_id
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
