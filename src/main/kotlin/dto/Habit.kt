package dto

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotliquery.Row

@Serializable
enum class HabitType(val value: String) {
    @SerialName("scheduled") SCHEDULED("scheduled"),
    @SerialName("counter") COUNTER("counter"),
    @SerialName("quantity") QUANTITY("quantity"),
    @SerialName("timer") TIMER("timer");

    companion object {
        fun parse(s: String?): HabitType = entries.firstOrNull { it.value == s } ?: SCHEDULED
    }
}

@Serializable
enum class Direction(val value: String) {
    @SerialName("more") MORE("more"),
    @SerialName("less") LESS("less");

    companion object {
        fun parse(s: String?): Direction? = entries.firstOrNull { it.value == s }
    }
}

@Serializable
enum class HabitStatus(val value: String) {
    @SerialName("active") ACTIVE("active"),
    @SerialName("paused") PAUSED("paused"),
    @SerialName("deleted") DELETED("deleted");

    companion object {
        fun parse(s: String?): HabitStatus = entries.firstOrNull { it.value == s } ?: ACTIVE
    }
}

@Serializable
enum class ParamType(val value: String) {
    @SerialName("number") NUMBER("number"),
    @SerialName("text") TEXT("text");

    companion object {
        fun parse(s: String?): ParamType? = entries.firstOrNull { it.value == s }
    }
}

/**
 * When a timer's extra annotation field is filled in: BEFORE the timer starts, or AFTER it stops.
 * Null on every other param (including the timer's own elapsed-seconds param).
 */
@Serializable
enum class TimerPhase(val value: String) {
    @SerialName("before") BEFORE("before"),
    @SerialName("after") AFTER("after");

    companion object {
        fun parse(s: String?): TimerPhase? = entries.firstOrNull { it.value == s }
    }
}

/**
 * A "field" of a habit, in its own `habit_params` row. Only quantity habits carry params now
 * (scheduled/counter events store everything on the `checkins` row), one per tracked field.
 * `paramType` is NUMBER for regular decimal fields, TEXT for free-text fields.
 */
@Serializable
data class HabitParam(
    val id: Long,
    @Transient val habitId: Long = 0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val name: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val unit: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val direction: Direction? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val dailyTarget: Double? = null,
    val position: Int = 0,
    val paramType: ParamType,
    // Set only on a timer's extra annotation fields, marking whether they're collected
    // before the timer starts or after it stops. Null on the duration param and elsewhere.
    @EncodeDefault(EncodeDefault.Mode.NEVER) val timerPhase: TimerPhase? = null,
)

/** A habit that an expired pause just flipped back to active, with enough to notify its owner. */
data class ResumedHabit(val userId: Long, val name: String, val langCode: String?)

@Serializable
data class Habit(
    val id: Long = 0L,
    @Transient val userId: Long = 0,
    val name: String,
    val type: HabitType,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val dailyTarget: Double? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val unit: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val direction: Direction? = null,
    val reminders: List<HabitReminder> = emptyList(),
    val status: HabitStatus = HabitStatus.ACTIVE,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val params: List<HabitParam> = emptyList(),
    @EncodeDefault(EncodeDefault.Mode.NEVER) val logOnly: Boolean = false,
) {
    /** A multi-field quantity habit: more than one param. Single-field habits hoist their
     *  param's metadata onto the habit row, so callers can treat them as plain habits.
     *  Timers may carry several params too (their extra annotation fields), but those are
     *  pure annotations — a timer is never a multi-field habit for stats/rendering. */
    val multiField: Boolean get() = type == HabitType.QUANTITY && params.size > 1
}

/** Maps a `habits` row. Reminders and params are loaded separately and filled in by the repository. */
fun Row.toHabit(): Habit = Habit(
    id = long("id"),
    userId = long("user_id"),
    name = string("name"),
    type = HabitType.parse(stringOrNull("habit_type")),
    dailyTarget = doubleOrNull("daily_target"),
    unit = stringOrNull("unit"),
    direction = Direction.parse(stringOrNull("direction")),
    status = HabitStatus.parse(stringOrNull("status")),
    logOnly = boolean("log_only"),
)

fun Row.toHabitParam(): HabitParam = HabitParam(
    id = long("id"),
    habitId = long("habit_id"),
    name = stringOrNull("name"),
    unit = stringOrNull("unit"),
    direction = Direction.parse(stringOrNull("direction")),
    dailyTarget = doubleOrNull("daily_target"),
    position = int("position"),
    paramType = ParamType.parse(string("param_type")) ?: error("habit_params.param_type is NULL"),
    timerPhase = TimerPhase.parse(stringOrNull("timer_phase")),
)

/** Reads a nullable Postgres int[] column; NULL becomes an empty list. */
fun Row.intArray(column: String): List<Int> {
    val arr = underlying.getArray(column) ?: return emptyList()
    return (arr.array as Array<*>).map { (it as Number).toInt() }.sorted()
}
