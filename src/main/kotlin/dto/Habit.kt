package dto

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotliquery.Row
import java.sql.Time
import java.time.LocalTime

@Serializable
enum class HabitType(val value: String) {
    @SerialName("scheduled") SCHEDULED("scheduled"),
    @SerialName("counter") COUNTER("counter"),
    @SerialName("quantity") QUANTITY("quantity");

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
data class Habit(
    val id: Long,
    @Transient val userId: Long = 0,
    val name: String,
    val type: HabitType,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val dailyTarget: Double? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val unit: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val direction: Direction? = null,
    val reminders: List<@Serializable(LocalTimeSerializer::class) LocalTime>,
    val status: HabitStatus,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val groupId: Long? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val fields: List<Habit> = emptyList(),
) {
    val isGroupRoot: Boolean get() = groupId != null && groupId == id
    val isGroupField: Boolean get() = groupId != null && groupId != id
}

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
        status = HabitStatus.parse(stringOrNull("status")),
        groupId = longOrNull("group_id"),
    )
}
