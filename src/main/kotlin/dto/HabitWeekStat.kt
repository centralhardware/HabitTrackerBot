package dto

data class HabitWeekStat(
    val habitId: Long,
    val name: String,
    val type: HabitType,
    val direction: Direction?,
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

data class WeekTotals(
    val done: Int,
    val skip: Int,
    val total: Int,
    val days: Int,
    val quantityTotal: Double,
    val quantityDays: Int
)
