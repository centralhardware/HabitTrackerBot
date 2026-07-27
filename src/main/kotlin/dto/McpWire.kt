package dto

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json

val McpJson: Json = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
    isLenient = true
}

@Serializable
data class CheckinsListArgs(
    val trackIds: List<Long> = emptyList(),
    val from: String? = null,
    val to: String? = null,
)

/** One track's check-ins in a batch [CheckinsListArgs] query. [valueDict] maps each value id used
 *  in this track's [checkins] to its actual value, so a value repeated across check-ins is spelled
 *  out once. (The per-param dictionary of recurring values lives on tracks_list, not here.) */
@Serializable
data class TrackCheckins(
    val trackId: Long,
    val found: Boolean,
    val checkins: List<CheckinRecord>,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val valueDict: Map<Int, FieldValue> = emptyMap(),
)

/** Batch [CheckinsListArgs] response: echoes the resolved date window so callers know what range they got. */
@Serializable
data class CheckinsListResult(
    val from: String,
    val to: String,
    val tracks: List<TrackCheckins>,
)

@Serializable
data class QuantityRecordArgs(
    val trackId: Long,
    val values: List<FieldValueArg> = emptyList(),
    val date: String? = null,
    val comment: String? = null,
)

/** Unified value arg: pass a number string (e.g. "5.5") for numeric params, any string for text params. */
@Serializable
data class FieldValueArg(
    val paramId: Long,
    val value: String,
)

/**
 * Parsed form of [FieldValueArg] after type dispatch against [dto.ParamType].
 * Serializes as a bare JSON value — a number for [Numeric], a string for [Text].
 */
@Serializable(with = FieldValueSerializer::class)
sealed interface FieldValue {
    /** This value rendered as a string — e.g. for the unified `checkin_values.value` column. */
    val asString: String

    data class Numeric(val v: Double) : FieldValue {
        override val asString get() = v.toString()
    }

    data class Text(val v: String) : FieldValue {
        override val asString get() = v
    }
}

object FieldValueSerializer : KSerializer<FieldValue> {
    override val descriptor = PrimitiveSerialDescriptor("dto.FieldValue", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: FieldValue) = when (value) {
        is FieldValue.Numeric -> encoder.encodeDouble(value.v)
        is FieldValue.Text -> encoder.encodeString(value.v)
    }

    // Output-only: CheckinRecord is never decoded, so a plain string read is enough.
    override fun deserialize(decoder: Decoder): FieldValue = FieldValue.Text(decoder.decodeString())
}

fun FieldValueArg.parse(paramType: ParamType): FieldValue? = when (paramType) {
    ParamType.TEXT -> value.trim().takeIf { it.isNotBlank() }?.let { FieldValue.Text(it) }
    ParamType.NUMBER -> value.replace(',', '.').toDoubleOrNull()?.let { FieldValue.Numeric(it) }
}

@Serializable
data class CheckinDeleteArgs(
    val checkinId: Long,
)

@Serializable
data class ParamValuesMergeArgs(
    val trackId: Long,
    val paramId: Long,
    val from: List<String> = emptyList(),
    val into: String,
)

/** One distinct value of a param's recurring-value dictionary and how many check-ins use it. */
@Serializable
data class ParamValueUsage(
    val value: String,
    val uses: Int,
)

@Serializable
data class CheckinUpdateArgs(
    val checkinId: Long,
    val comment: String? = null,
    val clearComment: Boolean = false,
    val values: List<FieldValueArg> = emptyList(),
)

/**
 * Edits a track and/or its fields in one call. Top-level scalars edit the track row: 'name', 'logOnly',
 * and — for single-field quantity/timer tracks — 'unit'/'dailyTarget'/
 * 'direction' (which a single-field track hoists onto the track itself). Per-field edits of a multi-field
 * track go in [params]. Each scalar uses a value/clear pair so callers can distinguish "leave unchanged"
 * (omit) from "set empty" (clearX = true).
 */
@Serializable
data class TrackUpdateArgs(
    val trackId: Long,
    val name: String? = null,
    val dailyTarget: Double? = null,
    val clearDailyTarget: Boolean = false,
    val unit: String? = null,
    val clearUnit: Boolean = false,
    val direction: String? = null,
    val clearDirection: Boolean = false,
    val logOnly: Boolean? = null,
    val params: List<TrackParamPatch> = emptyList(),
    /** Null = leave the schedule untouched; [] = clear all reminders; otherwise the full replacement set. */
    val reminders: List<ReminderArg>? = null,
)

/** One field's edit inside [TrackUpdateArgs.params]; same value/clear convention as the track scalars. */
@Serializable
data class TrackParamPatch(
    val paramId: Long,
    val name: String? = null,
    val clearName: Boolean = false,
    val unit: String? = null,
    val clearUnit: Boolean = false,
    val dailyTarget: Double? = null,
    val clearDailyTarget: Boolean = false,
    val direction: String? = null,
    val clearDirection: Boolean = false,
)

@Serializable
data class TrackParamDeleteArgs(
    val paramId: Long,
)

/**
 * Creates a new track. 'type' is "check" | "quantity" | "timer".
 * - check: a scheduled done/skip track — requires at least one reminder; takes no fields/unit/target.
 * - quantity: one or more numeric/text fields. Pass [params] for the fields, or leave it empty and
 *   give top-level unit/dailyTarget/direction for a single numeric field.
 * - timer: tracks elapsed time. Optional top-level dailyTarget (in seconds)/unit/direction, and
 *   optional [params] as before/after annotation fields (each with a timerPhase). Takes no reminders.
 * 'logOnly' makes it a journal (no targets/streaks/stats).
 */
@Serializable
data class TrackCreateArgs(
    val name: String,
    val type: String,
    val logOnly: Boolean = false,
    val unit: String? = null,
    val dailyTarget: Double? = null,
    val direction: String? = null,
    val params: List<TrackFieldArg> = emptyList(),
    val reminders: List<ReminderArg> = emptyList(),
)

/** One field of a new track. 'paramType' is "number" (default) or "text". 'timerPhase' ("before"/
 *  "after") applies to timer annotation fields only. unit/dailyTarget/direction apply to number fields. */
@Serializable
data class TrackFieldArg(
    val name: String? = null,
    val paramType: String = "number",
    val unit: String? = null,
    val dailyTarget: Double? = null,
    val direction: String? = null,
    val timerPhase: String? = null,
)

/** action is "pause" | "resume" | "delete"; pauseDays is honoured only for pause (0 = indefinite). */
@Serializable
data class TrackLifecycleArgs(
    val trackId: Long,
    val action: String,
    val pauseDays: Int = 0,
)

/** One reminder slot: give either "HH:MM" (hours 0..47 for next-day slots) or raw offsetMinutes. */
@Serializable
data class ReminderArg(
    val time: String? = null,
    val offsetMinutes: Int? = null,
    val days: List<Int> = emptyList(),
)
