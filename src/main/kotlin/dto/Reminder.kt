package dto

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotliquery.Row
import java.time.LocalDate

@Serializable
data class TrackReminder(
    @Transient val id: Long = 0,
    val offsetMinutes: Int,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val days: List<Int> = emptyList(),
)

fun Row.toTrackReminder(): TrackReminder = TrackReminder(
    id = long("id"),
    offsetMinutes = int("reminder_time"),
    days = intArray("reminder_days"),
)

data class DueReminder(
    val reminderId: Long,
    val trackId: Long,
    val trackType: TrackType,
    val userId: Long,
    val name: String,
    val offsetMinutes: Int,
    val userDate: LocalDate,
    val langCode: String?,
)

data class RawDue(
    val reminderId: Long,
    val trackId: Long,
    val trackType: TrackType,
    val userId: Long,
    val name: String,
    val offsetMinutes: Int,
    val tzId: String,
    val langCode: String?,
    val reminderDays: List<Int>,
)

data class RawMissed(
    val reminderId: Long,
    val trackId: Long,
    val userId: Long,
    val name: String,
    val offsetMinutes: Int,
    val langCode: String?,
    val missedDate: LocalDate,
)

fun Row.toRawDue(): RawDue = RawDue(
    reminderId = long("reminder_id"),
    trackId = long("track_id"),
    trackType = TrackType.parse(stringOrNull("track_type")),
    userId = long("user_id"),
    name = string("name"),
    offsetMinutes = int("reminder_time"),
    tzId = string("tz"),
    langCode = stringOrNull("lang"),
    reminderDays = intArray("reminder_days"),
)

fun Row.toRawMissed(): RawMissed = RawMissed(
    reminderId = long("reminder_id"),
    trackId = long("track_id"),
    userId = long("user_id"),
    name = string("name"),
    offsetMinutes = int("reminder_time"),
    langCode = stringOrNull("lang"),
    missedDate = localDate("missed_date"),
)
