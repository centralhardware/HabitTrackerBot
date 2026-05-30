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
    val habitIds: List<Long>,
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

@Serializable
data class QuantityRecordArgs(
    val habitId: Long,
    val values: List<FieldValueArg> = emptyList(),
    val date: String? = null,
    val comment: String? = null,
)

@Serializable
data class FieldValueArg(
    val paramId: Long,
    val value: Double,
)

@Serializable
data class CheckinDeleteArgs(
    val checkinId: Long,
)
