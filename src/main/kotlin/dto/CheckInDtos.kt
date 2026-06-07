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
    // A scheduled slot that's been sent but not yet answered (done/skip).
    @SerialName("pending") PENDING("pending"),
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
    val value: FieldValue? = null,
)

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
    // Null for counter events, which have no param (just a bare checkins row).
    @EncodeDefault(EncodeDefault.Mode.NEVER) val paramId: Long? = null,
    @Serializable(LocalDateSerializer::class) val date: LocalDate,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val status: CheckinStatus? = null,
    // One value per param: a JSON number for numeric params, a JSON string for text params.
    @EncodeDefault(EncodeDefault.Mode.NEVER) val value: FieldValue? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val offsetMinutes: Int? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val comment: String? = null,
)

/**
 * One `checkin_values` row joined with its parent `checkins` event — the raw unit the
 * analytics layer computes over. `isScheduled` mirrors `reminder_id IS NOT NULL`;
 * `paramId` distinguishes the fields of a multi-field habit.
 */
data class CheckinValueRow(
    val checkinId: Long,
    // Null for counter events (no checkin_values row) and scheduled events (a row, but no param —
    // it only carries done/skip status). Non-null only for quantity/text param values.
    val paramId: Long?,
    val date: LocalDate,
    val isScheduled: Boolean,
    val status: CheckinStatus?,
    val quantity: Double?,
    val comment: String?,
    val offsetMinutes: Int?,
    val textValue: String? = null,
    // Non-null on a timer's extra annotation field — a pure annotation the analytics layer
    // ignores entirely (never summed, never counted toward logged days).
    val timerPhase: TimerPhase? = null,
)

fun Row.toCheckinValueRow(): CheckinValueRow {
    val paramType = ParamType.parse(stringOrNull("param_type"))
    val rawValue = stringOrNull("value")
    return CheckinValueRow(
        checkinId = long("checkin_id"),
        paramId = longOrNull("param_id"),
        date = localDate("check_date"),
        isScheduled = longOrNull("reminder_id") != null,
        status = stringOrNull("status")?.let { v -> CheckinStatus.entries.firstOrNull { it.value == v } },
        quantity = if (paramType == ParamType.NUMBER) rawValue?.toDoubleOrNull() else null,
        comment = stringOrNull("comment"),
        offsetMinutes = intOrNull("reminder_time"),
        textValue = if (paramType == ParamType.TEXT) rawValue else null,
        timerPhase = TimerPhase.parse(stringOrNull("timer_phase")),
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
