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
    val habitIds: List<Long> = emptyList(),
    val from: String? = null,
    val to: String? = null,
)

/** One habit's check-ins in a batch [CheckinsListArgs] query. [paramValues] carries each param's
 *  dictionary of recurring values (empty/omitted for habits that have none yet). */
@Serializable
data class HabitCheckins(
    val habitId: Long,
    val found: Boolean,
    val checkins: List<CheckinRecord>,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val paramValues: List<ParamDictionary> = emptyList(),
)

/** The distinct dictionary (recurring) values of one param, with per-value usage counts. */
@Serializable
data class ParamDictionary(
    val paramId: Long,
    val values: List<ParamValueUsage>,
)

/** Batch [CheckinsListArgs] response: echoes the resolved date window so callers know what range they got. */
@Serializable
data class CheckinsListResult(
    val from: String,
    val to: String,
    val habits: List<HabitCheckins>,
)

@Serializable
data class QuantityRecordArgs(
    val habitId: Long,
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
data class CheckRecordArgs(
    val habitId: Long,
    val date: String? = null,
    val comment: String? = null,
)

@Serializable
data class CheckinDeleteArgs(
    val checkinId: Long,
)

@Serializable
data class ParamValuesMergeArgs(
    val habitId: Long,
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
