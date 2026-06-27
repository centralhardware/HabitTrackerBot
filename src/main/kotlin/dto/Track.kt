package dto

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotliquery.Row

@Serializable
enum class TrackType(val value: String) {
    // A "did I do the thing?" track. Its behavior is the product of two facts: whether it has
    // reminders (schedule -> markable done/skip slots) and whether it allows ad-hoc check-ins
    // (allowAdHoc -> a "+1" event any time). Merges the former `scheduled` and `counter` types.
    @SerialName("check") CHECK("check"),
    @SerialName("quantity") QUANTITY("quantity"),
    @SerialName("timer") TIMER("timer");

    companion object {
        fun parse(s: String?): TrackType = entries.firstOrNull { it.value == s } ?: CHECK
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
enum class TrackStatus(val value: String) {
    @SerialName("active") ACTIVE("active"),
    @SerialName("paused") PAUSED("paused"),
    @SerialName("deleted") DELETED("deleted");

    companion object {
        fun parse(s: String?): TrackStatus = entries.firstOrNull { it.value == s } ?: ACTIVE
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
 * A "field" of a track, in its own `track_params` row. Only quantity tracks carry params now
 * (scheduled/counter events store everything on the `checkins` row), one per tracked field.
 * `paramType` is NUMBER for regular decimal fields, TEXT for free-text fields.
 */
@Serializable
data class TrackParam(
    val id: Long,
    @Transient val trackId: Long = 0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val name: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val unit: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val direction: Direction? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val dailyTarget: Double? = null,
    val position: Int = 0,
    val paramType: ParamType,
    // Set only on a timer's extra annotation fields, marking whether they're collected
    // before the timer starts or after it stops. Null on the duration param and elsewhere.
    @EncodeDefault(EncodeDefault.Mode.NEVER) val timerPhase: TimerPhase? = null,
    // This param's dictionary of recurring (interned) values with usage counts, most-used first.
    // Populated for the tracks_list tool; empty/omitted elsewhere and when the param has none yet.
    @EncodeDefault(EncodeDefault.Mode.NEVER) val recurringValues: List<ParamValueUsage> = emptyList(),
)

/** A track that an expired pause just flipped back to active, with enough to notify its owner. */
data class ResumedTrack(val userId: Long, val name: String, val langCode: String?)

@Serializable
data class Track(
    val id: Long = 0L,
    @Transient val userId: Long = 0,
    val name: String,
    val type: TrackType,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val dailyTarget: Double? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val unit: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val direction: Direction? = null,
    val reminders: List<TrackReminder> = emptyList(),
    val status: TrackStatus = TrackStatus.ACTIVE,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val params: List<TrackParam> = emptyList(),
    @EncodeDefault(EncodeDefault.Mode.NEVER) val logOnly: Boolean = false,
    /** CHECK tracks only: whether arbitrary "+1" check-ins may be logged any time, independent
     *  of any schedule. A check track must have a schedule and/or this flag (never neither). */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val allowAdHoc: Boolean = false,
) {
    /** A multi-field quantity track: more than one param. Single-field tracks hoist their
     *  param's metadata onto the track row, so callers can treat them as plain tracks.
     *  Timers may carry several params too (their extra annotation fields), but those are
     *  pure annotations — a timer is never a multi-field track for stats/rendering. */
    val multiField: Boolean get() = type == TrackType.QUANTITY && params.size > 1

    /** A check track with reminders marks each fired occurrence as a done/skip slot. */
    val scheduled: Boolean get() = type == TrackType.CHECK && reminders.isNotEmpty()
}

/** Maps a `tracks` row. Reminders and params are loaded separately and filled in by the repository. */
fun Row.toTrack(): Track = Track(
    id = long("id"),
    userId = long("user_id"),
    name = string("name"),
    type = TrackType.parse(stringOrNull("track_type")),
    dailyTarget = doubleOrNull("daily_target"),
    unit = stringOrNull("unit"),
    direction = Direction.parse(stringOrNull("direction")),
    status = TrackStatus.parse(stringOrNull("status")),
    logOnly = boolean("log_only"),
    allowAdHoc = boolean("allow_adhoc"),
)

fun Row.toTrackParam(): TrackParam = TrackParam(
    id = long("id"),
    trackId = long("track_id"),
    name = stringOrNull("name"),
    unit = stringOrNull("unit"),
    direction = Direction.parse(stringOrNull("direction")),
    dailyTarget = doubleOrNull("daily_target"),
    position = int("position"),
    paramType = ParamType.parse(string("param_type")) ?: error("track_params.param_type is NULL"),
    timerPhase = TimerPhase.parse(stringOrNull("timer_phase")),
)

/** Reads a nullable Postgres int[] column; NULL becomes an empty list. */
fun Row.intArray(column: String): List<Int> {
    val arr = underlying.getArray(column) ?: return emptyList()
    return (arr.array as Array<*>).map { (it as Number).toInt() }.sorted()
}
