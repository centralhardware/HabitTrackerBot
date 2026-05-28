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

data class Checkin(
    val habitId: Long,
    val reminderId: Long?,
    val checkDate: LocalDate,
    val status: CheckinStatus?,
    val quantity: Double?,
    val commentId: Long? = null,
)

data class PendingCheckIn(
    val reminderId: Long,
    val name: String,
    val reminderTime: LocalTime,
    val date: LocalDate
)

@Serializable
data class CheckinRecord(
    @Serializable(LocalDateSerializer::class) val date: LocalDate,
    val status: CheckinStatus?,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val quantity: Double? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @Serializable(LocalTimeSerializer::class) val reminderTime: LocalTime? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val comment: String? = null,
)

data class DayCount(val date: LocalDate, val count: Int)
data class DayAmount(val date: LocalDate, val amount: Double)

fun Row.toPendingCheckIn(): PendingCheckIn = PendingCheckIn(
    reminderId = long("reminder_id"),
    name = string("name"),
    reminderTime = localTime("reminder_time"),
    date = localDate("check_date")
)

fun Row.toDayCount(): DayCount = DayCount(
    date = localDate("check_date"),
    count = int("cnt")
)

fun Row.toDayAmount(): DayAmount = DayAmount(
    date = localDate("check_date"),
    amount = double("amt")
)

fun Row.toCheckinRecord(): CheckinRecord = CheckinRecord(
    date = localDate("check_date"),
    status = stringOrNull("status")?.let { v -> CheckinStatus.entries.firstOrNull { it.value == v } },
    quantity = doubleOrNull("quantity"),
    reminderTime = localTimeOrNull("reminder_time"),
    comment = stringOrNull("comment"),
)
