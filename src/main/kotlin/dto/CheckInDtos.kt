package dto

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotliquery.Row
import java.time.Instant
import java.time.LocalDate

@Serializable
enum class CheckinStatus(val value: String) {
    @SerialName("done") DONE("done"),
    @SerialName("skip") SKIP("skip"),
    // A scheduled slot that's been sent but not yet answered (done/skip).
    @SerialName("pending") PENDING("pending"),
}

/** A check-in event: one row in `checkins`, owned by a single track. */
data class CheckinEvent(
    val userId: Long,
    val checkDate: LocalDate,
    val reminderId: Long?,
    val trackId: Long,
    val comment: String?,
)

/** One per-param value of an event: a row in `checkin_values`. The value is passed as plain text and
 *  store_param_value routes it by param type — numbers to the typed `value_num` column, text inline
 *  until it recurs and is interned into the `param_values` dictionary (V43/V44). */
data class CheckinValue(
    val paramId: Long,
    val status: CheckinStatus?,
    val value: FieldValue? = null,
)

/** A manual check-in event resolved for soft-deletion or editing: the event id, its track, date, comment and values. */
data class DeletableCheckin(
    val checkinId: Long,
    val trackId: Long,
    val date: LocalDate,
    val values: List<CheckinValue>,
    val comment: String? = null,
)

/**
 * One recent check-in event for the `/log` listing: the event, its owning track, and the values
 * recorded on it. [status] mirrors a scheduled slot's done/skip/pending (null for ad-hoc events);
 * [values] holds only real per-param values (the status-only row of a scheduled slot is excluded).
 */
data class RecentCheckin(
    val checkinId: Long,
    val trackId: Long,
    val date: LocalDate,
    val comment: String?,
    val reminderId: Long?,
    val status: CheckinStatus?,
    val values: List<CheckinValue>,
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

/** A track's check-ins with values already interned: [checkins] reference value ids that
 *  [valueDict] resolves to actual values. The service layer's output for the checkins_list tool. */
data class TrackCheckinPage(
    val checkins: List<CheckinRecord>,
    val valueDict: Map<Int, FieldValue>,
)

/** One check-in event with its raw per-param values, before they're interned into the track's
 *  [TrackCheckins.valueDict]. Internal to the analytics/service layer — not serialized. */
data class CheckinEventValues(
    val checkinId: Long,
    val date: LocalDate,
    val status: CheckinStatus?,
    val values: Map<Long, FieldValue>,
    val offsetMinutes: Int?,
    val comment: String?,
    val recordedAt: Instant?,
    val startedAt: Instant?,
)

/**
 * One whole check-in event (a single `checkins` row) for the wire. Its per-param values are
 * given as [values] — a map of paramId → an id into the track's [TrackCheckins.valueDict], so a
 * value repeated across many check-ins (e.g. the same book name) is spelled out only once in the
 * dictionary and referenced by id here. [values] is empty for a bare scheduled slot or a counter event.
 */
@Serializable
data class CheckinRecord(
    val checkinId: Long,
    @Serializable(LocalDateSerializer::class) val date: LocalDate,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val status: CheckinStatus? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val values: Map<Long, Int> = emptyMap(),
    @EncodeDefault(EncodeDefault.Mode.NEVER) val offsetMinutes: Int? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val comment: String? = null,
    // When the row was written (`checkins.checked_at`). For a timer this is the moment it was
    // stopped — i.e. the session's end. Null only for never-resolved pending scheduled slots.
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @Serializable(InstantSerializer::class) val recordedAt: Instant? = null,
    // The timer session's start, derived as recordedAt − elapsed seconds. Only set on a timer's
    // duration row; omitted for every other track type and for a timer's annotation fields.
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @Serializable(InstantSerializer::class) val startedAt: Instant? = null,
)

/**
 * One `checkin_values` row joined with its parent `checkins` event — the raw unit the
 * analytics layer computes over. `isScheduled` mirrors `reminder_id IS NOT NULL`;
 * `paramId` distinguishes the fields of a multi-field track.
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
    // `checkins.checked_at`: when the event was recorded. Null for never-resolved pending slots.
    val recordedAt: Instant? = null,
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
        recordedAt = instantOrNull("checked_at"),
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
