package db

import DatabaseService
import dto.DayAmount
import dto.DayCount
import dto.WeekTotals
import dto.toDayAmount
import dto.toDayCount
import dto.toWeekTotals
import kotliquery.queryOf
import kotliquery.sessionOf
import java.time.LocalDate

object WeeklySummaryRepository {

    fun weeklyTotals(habitId: Long, from: LocalDate, to: LocalDate): WeekTotals {
        return sessionOf(DatabaseService.dataSource).use { session ->
            session.run(
                queryOf(
                    """
                    SELECT
                        COUNT(*) FILTER (WHERE e.reminder_id IS NOT NULL AND v.status = 'done')             AS done,
                        COUNT(*) FILTER (WHERE e.reminder_id IS NOT NULL AND v.status = 'skip')             AS skip,
                        COUNT(*) FILTER (WHERE e.reminder_id IS NULL AND v.quantity IS NULL)                AS total,
                        COUNT(DISTINCT e.check_date) FILTER (WHERE e.reminder_id IS NULL AND v.quantity IS NULL) AS days,
                        COALESCE(SUM(v.quantity) FILTER (WHERE e.reminder_id IS NULL AND v.quantity IS NOT NULL), 0)::float AS qtotal,
                        COUNT(DISTINCT e.check_date) FILTER (WHERE e.reminder_id IS NULL AND v.quantity IS NOT NULL)        AS qdays
                    FROM checkins e
                    JOIN checkin_values v ON v.checkin_id = e.id
                    WHERE v.habit_id = ? AND e.check_date BETWEEN ?::date AND ?::date
                    """.trimIndent(),
                    habitId, from, to
                ).map { it.toWeekTotals() }.asSingle
            ) ?: WeekTotals(0, 0, 0, 0, 0.0, 0)
        }
    }

    fun counterCountsInRange(habitId: Long, from: LocalDate, to: LocalDate): List<DayCount> {
        return sessionOf(DatabaseService.dataSource).use { session ->
            session.run(
                queryOf(
                    """
                    SELECT e.check_date, COUNT(*) AS cnt
                    FROM checkins e
                    JOIN checkin_values v ON v.checkin_id = e.id
                    WHERE v.habit_id = ? AND e.reminder_id IS NULL
                      AND e.check_date BETWEEN ?::date AND ?::date
                    GROUP BY e.check_date
                    """.trimIndent(),
                    habitId, from, to
                ).map { it.toDayCount() }.asList
            )
        }
    }

    fun quantitySumsInRange(habitId: Long, from: LocalDate, to: LocalDate): List<DayAmount> {
        return sessionOf(DatabaseService.dataSource).use { session ->
            session.run(
                queryOf(
                    """
                    SELECT e.check_date, SUM(v.quantity)::float AS amt
                    FROM checkins e
                    JOIN checkin_values v ON v.checkin_id = e.id
                    WHERE v.habit_id = ? AND e.reminder_id IS NULL AND v.quantity IS NOT NULL
                      AND e.check_date BETWEEN ?::date AND ?::date
                    GROUP BY e.check_date
                    """.trimIndent(),
                    habitId, from, to
                ).map { it.toDayAmount() }.asList
            )
        }
    }
}
