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

/** A manual check-in event resolved for soft-deletion: the event id, its date and all its values. */
data class DeletableCheckin(
    val checkinId: Long,
    val date: LocalDate,
    val values: List<CheckinValue>,
)

data class PendingCheckIn(
    val reminderId: Long,
    val name: String,
    val reminderTime: LocalTime,
    val date: LocalDate
)

/** A scheduled-reminder message we sent, awaiting its check-in to be resolved. */
data class SentReminderMessage(
    val userId: Long,
    val messageId: Long,
    val text: String,
)

/** Identifies a scheduled check-in by its reminder and date — the key its messages share. */
data class ResolvedCheckin(
    val reminderId: Long,
    val checkDate: LocalDate,
)

@Serializable
data class CheckinRecord(
    val checkinId: Long,
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
    val checkinId: Long,
    val date: LocalDate,
    val isScheduled: Boolean,
    val status: CheckinStatus?,
    val quantity: Double?,
    val comment: String?,
    val reminderTime: LocalTime?,
)

fun Row.toCheckinValueRow(): CheckinValueRow = CheckinValueRow(
    checkinId = long("checkin_id"),
    date = localDate("check_date"),
    isScheduled = longOrNull("reminder_id") != null,
    status = stringOrNull("status")?.let { v -> CheckinStatus.entries.firstOrNull { it.value == v } },
    quantity = doubleOrNull("quantity"),
    comment = stringOrNull("comment"),
    reminderTime = localTimeOrNull("reminder_time"),
)

fun Row.toSentReminderMessage(): SentReminderMessage = SentReminderMessage(
    userId = long("user_id"),
    messageId = long("message_id"),
    text = string("text"),
)

fun Row.toResolvedCheckin(): ResolvedCheckin = ResolvedCheckin(
    reminderId = long("reminder_id"),
    checkDate = localDate("check_date"),
)

fun Row.toPendingCheckIn(): PendingCheckIn = PendingCheckIn(
    reminderId = long("reminder_id"),
    name = string("name"),
    reminderTime = localTime("reminder_time"),
    date = localDate("check_date")
)
