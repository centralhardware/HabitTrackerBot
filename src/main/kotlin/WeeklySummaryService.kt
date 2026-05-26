import db.WeeklySummaryRepository
import dto.Direction
import dto.Habit
import dto.HabitType
import dto.HabitWeekStat
import java.time.LocalDate

object WeeklySummaryService {

    fun weeklyStats(userId: Long, from: LocalDate, to: LocalDate): List<HabitWeekStat> {
        val habits = HabitService.listActive(userId)
        if (habits.isEmpty()) return emptyList()

        val flat: List<Pair<String, Habit>> = habits.flatMap { h ->
            if (h.isGroupRoot) h.fields.map { f -> "${h.name} / ${f.name}" to f }
            else listOf(h.name to h)
        }

        return flat.map { (displayName, h) ->
            val totals = WeeklySummaryRepository.weeklyTotals(h.id, from, to)
            val targetHitDays = computeTargetHits(h, from, to)
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

    private fun computeTargetHits(h: Habit, from: LocalDate, to: LocalDate): Int {
        val target = h.dailyTarget ?: return 0
        return when (h.type) {
            HabitType.COUNTER -> {
                val targetInt = target.toInt()
                WeeklySummaryRepository.counterCountsInRange(h.id, from, to).count { c ->
                    if (h.direction == Direction.LESS) c.count <= targetInt
                    else c.count >= targetInt
                }
            }
            HabitType.QUANTITY -> {
                val sums = WeeklySummaryRepository.quantitySumsInRange(h.id, from, to)
                sums.count { hits(it.amount, target, h.direction) }
            }
            HabitType.SCHEDULED -> 0
        }
    }

    private fun hits(value: Double, target: Double, direction: Direction?): Boolean =
        if (direction == Direction.LESS) value <= target else value >= target
}
