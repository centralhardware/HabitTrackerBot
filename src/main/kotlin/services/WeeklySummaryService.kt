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
                        hasSchedule = h.scheduled,
                        allowAdHoc = h.allowAdHoc,
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
                        hasSchedule = h.scheduled,
                        allowAdHoc = h.allowAdHoc,
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
        hasSchedule: Boolean,
        allowAdHoc: Boolean,
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
            targetHitDays = computeTargetHits(type, allowAdHoc, dailyTarget, direction, rows, from, to),
            hasSchedule = hasSchedule,
            allowAdHoc = allowAdHoc,
        )
    }

    private fun computeTargetHits(
        type: HabitType,
        allowAdHoc: Boolean,
        target: Double?,
        direction: Direction?,
        rows: List<CheckinValueRow>,
        from: LocalDate,
        to: LocalDate,
    ): Int {
        if (target == null) return 0
        return when (type) {
            // A check habit's daily target applies to its ad-hoc counts; no target without ad-hoc.
            HabitType.CHECK -> {
                if (!allowAdHoc) return 0
                val targetInt = target.toInt()
                CheckinAnalytics.counterCountsPerDay(rows, from, to).values.count { count ->
                    if (direction == Direction.LESS) count <= targetInt
                    else count >= targetInt
                }
            }
            HabitType.QUANTITY, HabitType.TIMER -> {
                CheckinAnalytics.quantitySumsPerDayInRange(rows, from, to).values
                    .count { hits(it, target, direction) }
            }
        }
    }

    private fun hits(value: Double, target: Double, direction: Direction?): Boolean =
        if (direction == Direction.LESS) value <= target else value >= target
}
