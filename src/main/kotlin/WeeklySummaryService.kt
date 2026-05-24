import kotliquery.queryOf
import kotliquery.sessionOf
import java.time.LocalDate

object WeeklySummaryService {

    data class HabitWeekStat(
        val habitId: Long,
        val name: String,
        val type: HabitService.Type,
        val direction: HabitService.Direction?,
        val dailyTarget: Double?,
        val unit: String?,
        val scheduledDone: Int,
        val scheduledSkip: Int,
        val counterTotal: Int,
        val counterDays: Int,
        val quantityTotal: Double,
        val quantityDays: Int,
        val targetHitDays: Int
    )

    fun weeklyStats(userId: Long, from: LocalDate, to: LocalDate): List<HabitWeekStat> {
        val habits = HabitService.listActive(userId)
        if (habits.isEmpty()) return emptyList()

        return sessionOf(DatabaseService.dataSource).use { session ->
            habits.map { h ->
                val totals = session.run(
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
                        h.id, from, to
                    ).map { r ->
                        WeekTotals(
                            done = r.int("done"),
                            skip = r.int("skip"),
                            total = r.int("total"),
                            days = r.int("days"),
                            quantityTotal = r.double("qtotal"),
                            quantityDays = r.int("qdays")
                        )
                    }.asSingle
                ) ?: WeekTotals(0, 0, 0, 0, 0.0, 0)

                val targetHitDays = when (h.type) {
                    HabitService.Type.COUNTER -> h.dailyTarget?.toInt()?.let { target ->
                        val hitExpr = if (h.direction == HabitService.Direction.LESS) "<=" else ">="
                        session.run(
                            queryOf(
                                """
                                SELECT COUNT(*) AS hit FROM (
                                    SELECT check_date, COUNT(*) AS cnt
                                    FROM checkins
                                    WHERE habit_id = ? AND reminder_id IS NULL
                                      AND check_date BETWEEN ?::date AND ?::date
                                    GROUP BY check_date
                                    HAVING COUNT(*) $hitExpr ?
                                ) sub
                                """.trimIndent(),
                                h.id, from, to, target
                            ).map { it.int("hit") }.asSingle
                        ) ?: 0
                    } ?: 0
                    HabitService.Type.QUANTITY -> h.dailyTarget?.let { target ->
                        val hitExpr = if (h.direction == HabitService.Direction.LESS) "<=" else ">="
                        session.run(
                            queryOf(
                                """
                                SELECT COUNT(*) AS hit FROM (
                                    SELECT check_date, SUM(quantity) AS amt
                                    FROM checkins
                                    WHERE habit_id = ? AND reminder_id IS NULL AND quantity IS NOT NULL
                                      AND check_date BETWEEN ?::date AND ?::date
                                    GROUP BY check_date
                                    HAVING SUM(quantity) $hitExpr ?
                                ) sub
                                """.trimIndent(),
                                h.id, from, to, target
                            ).map { it.int("hit") }.asSingle
                        ) ?: 0
                    } ?: 0
                    else -> 0
                }

                HabitWeekStat(
                    habitId = h.id,
                    name = h.name,
                    type = h.type,
                    direction = h.direction,
                    dailyTarget = h.dailyTarget,
                    unit = h.unit,
                    scheduledDone = totals.done,
                    scheduledSkip = totals.skip,
                    counterTotal = totals.total,
                    counterDays = totals.days,
                    quantityTotal = totals.quantityTotal,
                    quantityDays = totals.quantityDays,
                    targetHitDays = targetHitDays
                )
            }
        }
    }

    private data class WeekTotals(
        val done: Int,
        val skip: Int,
        val total: Int,
        val days: Int,
        val quantityTotal: Double,
        val quantityDays: Int
    )
}
