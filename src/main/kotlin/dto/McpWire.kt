package dto

import kotlinx.serialization.Serializable
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

/** One habit's check-ins in a batch [CheckinsListArgs] query. */
@Serializable
data class HabitCheckins(
    val habitId: Long,
    val found: Boolean,
    val checkins: List<CheckinRecord>,
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

/** Parsed form of [FieldValueArg] after type dispatch against [dto.ParamType]. */
sealed interface FieldValue {
    data class Numeric(val v: Double) : FieldValue
    data class Text(val v: String) : FieldValue
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
data class CheckinUpdateArgs(
    val checkinId: Long,
    val comment: String? = null,
    val clearComment: Boolean = false,
    val values: List<FieldValueArg> = emptyList(),
)
