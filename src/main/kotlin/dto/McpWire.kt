package dto

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

val McpJson: Json = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
    isLenient = true
}

@Serializable
data class CheckinRecordArgs(
    val habitId: Long,
    val value: Double? = null,
    val date: String? = null,
    val reminderTime: String? = null,
    val status: String? = null,
    val comment: String? = null,
)

@Serializable
data class CheckinsListArgs(
    val habitId: Long,
    val from: String? = null,
    val to: String? = null,
)

@Serializable
data class QuantityGroupRecordArgs(
    val habitId: Long,
    val values: List<FieldValueArg>,
    val date: String? = null,
    val comment: String? = null,
)

@Serializable
data class FieldValueArg(
    val fieldId: Long,
    val value: Double,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class McpProp(
    val type: String,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val pattern: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val exclusiveMinimum: Int? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val enum: List<String>? = null,
)
