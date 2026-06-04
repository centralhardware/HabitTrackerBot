package dto

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotliquery.Row
import java.time.LocalDate

@Serializable
enum class CheckinStatus(val value: String) {
    @SerialName("done") DONE("done"),
    @SerialName("skip") SKIP("skip"),
}

/** A check-in event: one row in `checkins`, owned by a single habit. */
data class CheckinEvent(
    val userId: Long,
    val checkDate: LocalDate,
    val reminderId: Long?,
    val habitId: Long,
    val comment: String?,
)

/** One per-param value of an event: a row in `checkin_values`. */
data class CheckinValue(
    val paramId: Long,
    val status: CheckinStatus?,
    val quantity: Double?,
    val textValue: String? = null,
) {
    /** Unified string written to `checkin_values.value`. */
    val dbValue: String? get() = quantity?.toString() ?: textValue
}

/** A manual check-in event resolved for soft-deletion or editing: the event id, its habit, date, comment and values. */
data class DeletableCheckin(
    val checkinId: Long,
    val habitId: Long,
    val date: LocalDate,
    val values: List<CheckinValue>,
    val comment: String? = null,
)

data class PendingCheckIn(
    val reminderId: Long,
    val name: String,
    val offsetMinutes: Int,
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
    val paramId: Long,
    @Serializable(LocalDateSerializer::class) val date: LocalDate,
    val status: CheckinStatus?,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val quantity: Double? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val offsetMinutes: Int? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val comment: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val textValue: String? = null,
)

/**
 * One `checkin_values` row joined with its parent `checkins` event — the raw unit the
 * analytics layer computes over. `isScheduled` mirrors `reminder_id IS NOT NULL`;
 * `paramId` distinguishes the fields of a multi-field habit.
 */
data class CheckinValueRow(
    val checkinId: Long,
    val paramId: Long,
    val date: LocalDate,
    val isScheduled: Boolean,
    val status: CheckinStatus?,
    val quantity: Double?,
    val comment: String?,
    val offsetMinutes: Int?,
    val textValue: String? = null,
)

fun Row.toCheckinValueRow(): CheckinValueRow {
    val paramType = ParamType.parse(stringOrNull("param_type"))
    val rawValue = stringOrNull("value")
    return CheckinValueRow(
        checkinId = long("checkin_id"),
        // Counter events have no checkin_values row, so param_id is NULL (0 stands in — counter
        // habits are single-param, so the value is never used to distinguish fields).
        paramId = longOrNull("param_id") ?: 0L,
        date = localDate("check_date"),
        isScheduled = longOrNull("reminder_id") != null,
        status = stringOrNull("status")?.let { v -> CheckinStatus.entries.firstOrNull { it.value == v } },
        quantity = if (paramType == ParamType.NUMBER) rawValue?.toDoubleOrNull() else null,
        comment = stringOrNull("comment"),
        offsetMinutes = intOrNull("reminder_time"),
        textValue = if (paramType == ParamType.TEXT) rawValue else null,
    )
}

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
    offsetMinutes = int("reminder_time"),
    date = localDate("check_date")
)
