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
                        COUNT(*) FILTER (WHERE reminder_id IS NOT NULL AND status = 'done')             AS done,
                        COUNT(*) FILTER (WHERE reminder_id IS NOT NULL AND status = 'skip')             AS skip,
                        COUNT(*) FILTER (WHERE reminder_id IS NULL AND quantity IS NULL)                AS total,
                        COUNT(DISTINCT check_date) FILTER (WHERE reminder_id IS NULL AND quantity IS NULL) AS days,
                        COALESCE(SUM(quantity) FILTER (WHERE reminder_id IS NULL AND quantity IS NOT NULL), 0)::float AS qtotal,
                        COUNT(DISTINCT check_date) FILTER (WHERE reminder_id IS NULL AND quantity IS NOT NULL)        AS qdays
                    FROM checkins
                    WHERE habit_id = ? AND check_date BETWEEN ?::date AND ?::date
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
                    SELECT check_date, COUNT(*) AS cnt
                    FROM checkins
                    WHERE habit_id = ? AND reminder_id IS NULL
                      AND check_date BETWEEN ?::date AND ?::date
                    GROUP BY check_date
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
                    SELECT check_date, SUM(quantity)::float AS amt
                    FROM checkins
                    WHERE habit_id = ? AND reminder_id IS NULL AND quantity IS NOT NULL
                      AND check_date BETWEEN ?::date AND ?::date
                    GROUP BY check_date
                    """.trimIndent(),
                    habitId, from, to
                ).map { it.toDayAmount() }.asList
            )
        }
    }
}
