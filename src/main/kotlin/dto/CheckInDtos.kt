package dto

import kotliquery.Row
import java.time.LocalDate
import java.time.LocalTime

enum class CheckinStatus(val value: String) {
    DONE("done"),
    SKIP("skip")
}

data class Checkin(
    val habitId: Long,
    val reminderId: Long?,
    val checkDate: LocalDate,
    val status: CheckinStatus?,
    val quantity: Double?
)

data class DayStatus(
    val date: LocalDate,
    val allDone: Boolean
)

data class PendingCheckIn(
    val reminderId: Long,
    val name: String,
    val reminderTime: LocalTime,
    val date: LocalDate
)

data class DayCount(val date: LocalDate, val count: Int)
data class DayAmount(val date: LocalDate, val amount: Double)

data class ScheduledTotals(val totalDays: Int, val doneCount: Int, val skipCount: Int)

fun Row.toDayStatus(): DayStatus = DayStatus(
    date = localDate("check_date"),
    allDone = boolean("day_done")
)

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

fun Row.toScheduledTotals(): ScheduledTotals = ScheduledTotals(
    totalDays = int("total_days"),
    doneCount = int("done_count"),
    skipCount = int("skip_count")
)
