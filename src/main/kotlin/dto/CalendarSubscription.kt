package dto

import kotliquery.Row
import java.time.Instant

/** A user's iCal subscription: its content flags and bookkeeping. The token itself is never stored. */
data class CalendarSubscription(
    val userId: Long,
    val includeCheckins: Boolean,
    val includeReminders: Boolean,
    val createdAt: Instant,
    val lastUsedAt: Instant?,
)

fun Row.toCalendarSubscription(): CalendarSubscription = CalendarSubscription(
    userId = long("user_id"),
    includeCheckins = boolean("include_checkins"),
    includeReminders = boolean("include_reminders"),
    createdAt = instant("created_at"),
    lastUsedAt = instantOrNull("last_used_at"),
)

/** A logged check-in flattened for the calendar feed, carrying its habit's name. */
data class CalendarCheckin(
    val checkinId: Long,
    val habitName: String,
    val date: java.time.LocalDate,
    val status: String?,
    val value: String?,
    val paramType: String?,
    val comment: String?,
    val checkedAt: Instant?,
)

fun Row.toCalendarCheckin(): CalendarCheckin = CalendarCheckin(
    checkinId = long("checkin_id"),
    habitName = string("name"),
    date = localDate("check_date"),
    status = stringOrNull("status"),
    value = stringOrNull("value"),
    paramType = stringOrNull("param_type"),
    comment = stringOrNull("comment"),
    checkedAt = instantOrNull("checked_at"),
)

/** A habit reminder flattened for the calendar feed: time-of-day and ISO weekdays (Mon=1..Sun=7). */
data class CalendarReminder(
    val reminderId: Long,
    val habitName: String,
    val offsetMinutes: Int,
    val days: List<Int>,
)

fun Row.toCalendarReminder(): CalendarReminder = CalendarReminder(
    reminderId = long("id"),
    habitName = string("name"),
    offsetMinutes = int("reminder_time"),
    days = intArray("reminder_days"),
)
