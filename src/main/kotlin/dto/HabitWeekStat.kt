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
    val targetHitDays: Int,
    // CHECK habits only: whether the habit has reminder slots (done/skip block) and/or
    // allows ad-hoc check-ins (counter block). Both blocks may be rendered.
    val hasSchedule: Boolean = false,
    val allowAdHoc: Boolean = false,
)

data class WeekTotals(
    val done: Int,
    val skip: Int,
    val total: Int,
    val days: Int,
    val quantityTotal: Double,
    val quantityDays: Int
)
