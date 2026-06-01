package dto

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotliquery.Row
import java.time.LocalDate

@Serializable
data class HabitReminder(
    @Transient val id: Long = 0,
    val offsetMinutes: Int,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val days: List<Int> = emptyList(),
)

fun Row.toHabitReminder(): HabitReminder = HabitReminder(
    id = long("id"),
    offsetMinutes = int("reminder_time"),
    days = intArray("reminder_days"),
)

data class DueReminder(
    val reminderId: Long,
    val habitId: Long,
    val habitType: HabitType,
    val userId: Long,
    val name: String,
    val offsetMinutes: Int,
    val userDate: LocalDate,
    val langCode: String?,
)

data class RawDue(
    val reminderId: Long,
    val habitId: Long,
    val habitType: HabitType,
    val userId: Long,
    val name: String,
    val offsetMinutes: Int,
    val tzId: String,
    val langCode: String?,
    val reminderDays: List<Int>,
)

data class RawMissed(
    val reminderId: Long,
    val habitId: Long,
    val userId: Long,
    val name: String,
    val offsetMinutes: Int,
    val langCode: String?,
    val missedDate: LocalDate,
)

data class DueUser(
    val userId: Long,
    val today: LocalDate,
    val langCode: String?
)

fun Row.toRawDue(): RawDue = RawDue(
    reminderId = long("reminder_id"),
    habitId = long("habit_id"),
    habitType = HabitType.parse(stringOrNull("habit_type")),
    userId = long("user_id"),
    name = string("name"),
    offsetMinutes = int("reminder_time"),
    tzId = string("tz"),
    langCode = stringOrNull("lang"),
    reminderDays = intArray("reminder_days"),
)

fun Row.toRawMissed(): RawMissed = RawMissed(
    reminderId = long("reminder_id"),
    habitId = long("habit_id"),
    userId = long("user_id"),
    name = string("name"),
    offsetMinutes = int("reminder_time"),
    langCode = stringOrNull("lang"),
    missedDate = localDate("missed_date"),
)

fun Row.toDueUser(): DueUser = DueUser(
    userId = long("user_id"),
    today = localDate("today"),
    langCode = stringOrNull("language")
)
