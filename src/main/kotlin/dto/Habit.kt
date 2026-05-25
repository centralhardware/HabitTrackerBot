package dto

import kotliquery.Row
import java.sql.Time
import java.time.LocalTime

enum class HabitType(val value: String) {
    SCHEDULED("scheduled"),
    COUNTER("counter"),
    QUANTITY("quantity");

    companion object {
        fun parse(s: String?): HabitType = entries.firstOrNull { it.value == s } ?: SCHEDULED
    }
}

enum class Direction(val value: String) {
    MORE("more"),
    LESS("less");

    companion object {
        fun parse(s: String?): Direction? = entries.firstOrNull { it.value == s }
    }
}

enum class HabitStatus(val value: String) {
    ACTIVE("active"),
    PAUSED("paused"),
    DELETED("deleted");

    companion object {
        fun parse(s: String?): HabitStatus = entries.firstOrNull { it.value == s } ?: ACTIVE
    }
}

data class Habit(
    val id: Long,
    val userId: Long,
    val name: String,
    val type: HabitType,
    val dailyTarget: Double?,
    val unit: String?,
    val direction: Direction?,
    val reminders: List<LocalTime>,
    val status: HabitStatus
)

fun Row.toHabit(): Habit {
    @Suppress("UNCHECKED_CAST")
    val times = underlying.getArray("times").array as Array<Time>
    return Habit(
        id = long("id"),
        userId = long("user_id"),
        name = string("name"),
        type = HabitType.parse(stringOrNull("habit_type")),
        dailyTarget = doubleOrNull("daily_target"),
        unit = stringOrNull("unit"),
        direction = Direction.parse(stringOrNull("direction")),
        reminders = times.map { it.toLocalTime() },
        status = HabitStatus.parse(stringOrNull("status"))
    )
}
