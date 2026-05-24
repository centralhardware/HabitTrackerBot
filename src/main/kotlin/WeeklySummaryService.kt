import kotliquery.queryOf
import kotliquery.sessionOf
import java.time.LocalDate

object WeeklySummaryService {

    data class HabitWeekStat(
        val habitId: Long,
        val name: String,
        val type: HabitService.Type,
        val direction: HabitService.Direction?,
        val dailyTarget: Int?,
        val scheduledDone: Int,
        val scheduledSkip: Int,
        val counterTotal: Int,
        val counterDays: Int,
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
                            COUNT(*) FILTER (WHERE reminder_id IS NOT NULL AND status = 'done') AS done,
                            COUNT(*) FILTER (WHERE reminder_id IS NOT NULL AND status = 'skip') AS skip,
                            COUNT(*) FILTER (WHERE reminder_id IS NULL)                          AS total,
                            COUNT(DISTINCT check_date) FILTER (WHERE reminder_id IS NULL)        AS days
                        FROM checkins
                        WHERE habit_id = ? AND check_date BETWEEN ?::date AND ?::date
                        """.trimIndent(),
                        h.id, from, to
                    ).map { r ->
                        WeekTotals(
                            done = r.int("done"),
                            skip = r.int("skip"),
                            total = r.int("total"),
                            days = r.int("days")
                        )
                    }.asSingle
                ) ?: WeekTotals(0, 0, 0, 0)

                val target = h.dailyTarget
                val targetHitDays = if (target != null && h.type == HabitService.Type.COUNTER) {
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
                } else 0

                HabitWeekStat(
                    habitId = h.id,
                    name = h.name,
                    type = h.type,
                    direction = h.direction,
                    dailyTarget = target,
                    scheduledDone = totals.done,
                    scheduledSkip = totals.skip,
                    counterTotal = totals.total,
                    counterDays = totals.days,
                    targetHitDays = targetHitDays
                )
            }
        }
    }

    private data class WeekTotals(val done: Int, val skip: Int, val total: Int, val days: Int)
}
