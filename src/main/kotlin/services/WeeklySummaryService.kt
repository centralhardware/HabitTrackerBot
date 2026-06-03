package services

import db.CheckInRepository
import dto.CheckinValueRow
import dto.Direction
import dto.HabitStatus
import dto.HabitType
import dto.HabitWeekStat
import java.time.LocalDate

object WeeklySummaryService {

    fun weeklyStats(userId: Long, from: LocalDate, to: LocalDate): List<HabitWeekStat> {
        val habits = HabitService.listActive(userId)
            .filter { it.status == HabitStatus.ACTIVE && !it.logOnly }
        if (habits.isEmpty()) return emptyList()

        return habits.flatMap { h ->
            val rows = CheckInRepository.loadForHabit(h.id)
            if (h.multiField) {
                h.params.map { p ->
                    weekStat(
                        name = "${h.name} / ${p.name}",
                        habitId = h.id,
                        type = h.type,
                        direction = p.direction,
                        dailyTarget = p.dailyTarget,
                        unit = p.unit,
                        rows = rows.filter { it.paramId == p.id },
                        from = from,
                        to = to,
                    )
                }
            } else {
                listOf(
                    weekStat(
                        name = h.name,
                        habitId = h.id,
                        type = h.type,
                        direction = h.direction,
                        dailyTarget = h.dailyTarget,
                        unit = h.unit,
                        rows = rows,
                        from = from,
                        to = to,
                    )
                )
            }
        }
    }

    private fun weekStat(
        name: String,
        habitId: Long,
        type: HabitType,
        direction: Direction?,
        dailyTarget: Double?,
        unit: String?,
        rows: List<CheckinValueRow>,
        from: LocalDate,
        to: LocalDate,
    ): HabitWeekStat {
        val totals = CheckinAnalytics.weekTotals(rows, from, to)
        return HabitWeekStat(
            habitId = habitId,
            name = name,
            type = type,
            direction = direction,
            dailyTarget = dailyTarget,
            unit = unit,
            scheduledDone = totals.done,
            scheduledSkip = totals.skip,
            counterTotal = totals.total,
            counterDays = totals.days,
            quantityTotal = totals.quantityTotal,
            quantityDays = totals.quantityDays,
            targetHitDays = computeTargetHits(type, dailyTarget, direction, rows, from, to),
        )
    }

    private fun computeTargetHits(
        type: HabitType,
        target: Double?,
        direction: Direction?,
        rows: List<CheckinValueRow>,
        from: LocalDate,
        to: LocalDate,
    ): Int {
        if (target == null) return 0
        return when (type) {
            HabitType.COUNTER -> {
                val targetInt = target.toInt()
                CheckinAnalytics.counterCountsPerDay(rows, from, to).values.count { count ->
                    if (direction == Direction.LESS) count <= targetInt
                    else count >= targetInt
                }
            }
            HabitType.QUANTITY -> {
                CheckinAnalytics.quantitySumsPerDayInRange(rows, from, to).values
                    .count { hits(it, target, direction) }
            }
            HabitType.SCHEDULED -> 0
        }
    }

    private fun hits(value: Double, target: Double, direction: Direction?): Boolean =
        if (direction == Direction.LESS) value <= target else value >= target
}
