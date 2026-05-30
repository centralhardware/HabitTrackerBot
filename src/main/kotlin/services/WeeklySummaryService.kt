package services

import db.CheckInRepository
import dto.CheckinValueRow
import dto.Direction
import dto.Habit
import dto.HabitType
import dto.HabitWeekStat
import java.time.LocalDate

object WeeklySummaryService {

    fun weeklyStats(userId: Long, from: LocalDate, to: LocalDate): List<HabitWeekStat> {
        // Log-only habits are pure journals; they never appear in the weekly summary.
        val habits = HabitService.listActive(userId).filterNot { it.logOnly }
        if (habits.isEmpty()) return emptyList()

        val flat: List<Pair<String, Habit>> = habits.flatMap { h ->
            if (h.isGroupRoot) h.fields.map { f -> "${h.name} / ${f.name}" to f }
            else listOf(h.name to h)
        }

        return flat.map { (displayName, h) ->
            val rows = CheckInRepository.loadForHabit(h.id)
            val totals = CheckinAnalytics.weekTotals(rows, from, to)
            val targetHitDays = computeTargetHits(h, rows, from, to)
            HabitWeekStat(
                habitId = h.id,
                name = displayName,
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

    private fun computeTargetHits(h: Habit, rows: List<CheckinValueRow>, from: LocalDate, to: LocalDate): Int {
        val target = h.dailyTarget ?: return 0
        return when (h.type) {
            HabitType.COUNTER -> {
                val targetInt = target.toInt()
                CheckinAnalytics.counterCountsPerDay(rows, from, to).values.count { count ->
                    if (h.direction == Direction.LESS) count <= targetInt
                    else count >= targetInt
                }
            }
            HabitType.QUANTITY -> {
                CheckinAnalytics.quantitySumsPerDayInRange(rows, from, to).values
                    .count { hits(it, target, h.direction) }
            }
            HabitType.SCHEDULED -> 0
        }
    }

    private fun hits(value: Double, target: Double, direction: Direction?): Boolean =
        if (direction == Direction.LESS) value <= target else value >= target
}
