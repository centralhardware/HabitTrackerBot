package dto

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotliquery.Row
import java.time.LocalDate
import java.time.LocalTime

@Serializable
enum class CheckinStatus(val value: String) {
    @SerialName("done") DONE("done"),
    @SerialName("skip") SKIP("skip"),
}

data class CheckinEvent(
    val userId: Long,
    val checkDate: LocalDate,
    val reminderId: Long?,
    val comment: String?,
)

data class CheckinValue(
    val habitId: Long,
    val status: CheckinStatus?,
    val quantity: Double?,
)

data class PendingCheckIn(
    val reminderId: Long,
    val name: String,
    val reminderTime: LocalTime,
    val date: LocalDate
)

@Serializable
data class CheckinRecord(
    @Serializable(LocalDateSerializer::class) val date: LocalDate,
    val status: CheckinStatus?,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val quantity: Double? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @Serializable(LocalTimeSerializer::class) val reminderTime: LocalTime? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val comment: String? = null,
)

/**
 * One `checkin_values` row joined with its parent `checkins` event — the raw unit the
 * analytics layer computes over. `isScheduled` mirrors `reminder_id IS NOT NULL`.
 */
data class CheckinValueRow(
    val date: LocalDate,
    val isScheduled: Boolean,
    val status: CheckinStatus?,
    val quantity: Double?,
    val comment: String?,
    val reminderTime: LocalTime?,
)

fun Row.toCheckinValueRow(): CheckinValueRow = CheckinValueRow(
    date = localDate("check_date"),
    isScheduled = longOrNull("reminder_id") != null,
    status = stringOrNull("status")?.let { v -> CheckinStatus.entries.firstOrNull { it.value == v } },
    quantity = doubleOrNull("quantity"),
    comment = stringOrNull("comment"),
    reminderTime = localTimeOrNull("reminder_time"),
)

fun Row.toPendingCheckIn(): PendingCheckIn = PendingCheckIn(
    reminderId = long("reminder_id"),
    name = string("name"),
    reminderTime = localTime("reminder_time"),
    date = localDate("check_date")
)

fun Row.toCheckinRecord(): CheckinRecord = CheckinRecord(
    date = localDate("check_date"),
    status = stringOrNull("status")?.let { v -> CheckinStatus.entries.firstOrNull { it.value == v } },
    quantity = doubleOrNull("quantity"),
    reminderTime = localTimeOrNull("reminder_time"),
    comment = stringOrNull("comment"),
)
