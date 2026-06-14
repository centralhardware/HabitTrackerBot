package services

import db.CalendarFeedRepository
import dto.CalendarCheckin
import dto.CalendarReminder
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Renders a user's habits as an iCalendar (RFC 5545) document for calendar-app subscriptions:
 * logged check-ins as all-day events and reminders as recurring timed events. Read-only.
 */
object CalendarFeedService {

    // How far back check-in events are exported. Subscriptions refresh periodically, so a bounded
    // window keeps the feed small without losing anything a user cares to see in their calendar.
    private const val HISTORY_DAYS = 180L

    private val DATE = DateTimeFormatter.ofPattern("yyyyMMdd")
    private val STAMP = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
    private val LOCAL = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
    private val BYDAY = arrayOf("MO", "TU", "WE", "TH", "FR", "SA", "SU")

    fun build(userId: Long, tz: ZoneId, includeCheckins: Boolean, includeReminders: Boolean): String {
        val stamp = STAMP.format(Instant.now().atZone(ZoneOffset.UTC))
        val sb = StringBuilder()
        line(sb, "BEGIN:VCALENDAR")
        line(sb, "VERSION:2.0")
        line(sb, "PRODID:-//HabitTrackerBot//Habits//EN")
        line(sb, "CALSCALE:GREGORIAN")
        line(sb, "METHOD:PUBLISH")
        line(sb, "X-WR-CALNAME:Habits")
        line(sb, "X-WR-TIMEZONE:${tz.id}")

        if (includeCheckins) {
            val since = LocalDate.now(tz).minusDays(HISTORY_DAYS)
            CalendarFeedRepository.checkinsSince(userId, since)
                .groupBy { it.checkinId }
                .values
                .forEach { rows -> checkinEvent(rows)?.let { writeAllDay(sb, it, stamp) } }
        }
        if (includeReminders) {
            CalendarFeedRepository.reminders(userId).forEach { writeReminder(sb, it, stamp) }
        }

        line(sb, "END:VCALENDAR")
        return sb.toString()
    }

    /** An all-day check-in event, or null if the grouped event isn't a logged one (skip/pending). */
    private data class CheckinEvent(val id: Long, val name: String, val date: LocalDate, val details: String?)

    private fun checkinEvent(rows: List<CalendarCheckin>): CheckinEvent? {
        val first = rows.first()
        val statuses = rows.mapNotNull { it.status }
        // No status at all = a counter/manual event (always logged); otherwise require a "done".
        val logged = if (statuses.isEmpty()) true else statuses.any { it == "done" }
        if (!logged) return null
        val values = rows.mapNotNull { formatValue(it) }
        val details = (values + listOfNotNull(first.comment?.takeIf { it.isNotBlank() }))
            .joinToString("\n").ifBlank { null }
        return CheckinEvent(first.checkinId, first.habitName, first.date, details)
    }

    private fun formatValue(c: CalendarCheckin): String? {
        val raw = c.value?.takeIf { it.isNotBlank() } ?: return null
        // A timer's unnamed numeric field is its elapsed seconds — show it as a readable duration
        // rather than a bare second count.
        if (c.habitType == "timer" && c.paramType == "number" && c.paramName.isNullOrBlank()) {
            return raw.toDoubleOrNull()?.let { humanizeSeconds(it.toLong()) } ?: raw
        }
        val v = if (c.paramType == "number") raw.toDoubleOrNull()?.let(::trimNumber) ?: raw else raw
        // Numeric fields carry a unit; text fields don't. A field name (multi-field habits) labels
        // the value so bare numbers don't appear context-free in the calendar.
        val withUnit = if (c.paramType == "number" && !c.unit.isNullOrBlank()) "$v ${c.unit}" else v
        return c.paramName?.takeIf { it.isNotBlank() }?.let { "$it: $withUnit" } ?: withUnit
    }

    private fun trimNumber(d: Double): String =
        if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString()

    private fun humanizeSeconds(total: Long): String {
        val h = total / 3600
        val m = total % 3600 / 60
        val s = total % 60
        return buildString {
            if (h > 0) append("${h}h ")
            if (m > 0) append("${m}m ")
            if (s > 0 || isEmpty()) append("${s}s")
        }.trim()
    }

    private fun writeAllDay(sb: StringBuilder, ev: CheckinEvent, stamp: String) {
        line(sb, "BEGIN:VEVENT")
        line(sb, "UID:checkin-${ev.id}@habittracker")
        line(sb, "DTSTAMP:$stamp")
        line(sb, "DTSTART;VALUE=DATE:${DATE.format(ev.date)}")
        line(sb, "DTEND;VALUE=DATE:${DATE.format(ev.date.plusDays(1))}")
        line(sb, "SUMMARY:${escape(ev.name)}")
        ev.details?.let { line(sb, "DESCRIPTION:${escape(it)}") }
        line(sb, "TRANSP:TRANSPARENT")
        line(sb, "END:VEVENT")
    }

    private fun writeReminder(sb: StringBuilder, rem: CalendarReminder, stamp: String) {
        val time = LocalTime.of(rem.offsetMinutes / 60 % 24, rem.offsetMinutes % 60)
        val today = LocalDate.now()
        // Anchor on the next day that matches one of the reminder's weekdays (today for a daily one),
        // so the recurrence's first instance lands on a valid BYDAY. Times are floating-local: the
        // viewer sees them at the configured wall-clock time in their own calendar timezone.
        val anchorDate = if (rem.days.isEmpty()) today
        else (0L..6L).map { today.plusDays(it) }.first { it.dayOfWeek.value in rem.days }
        val start = anchorDate.atTime(time)
        line(sb, "BEGIN:VEVENT")
        line(sb, "UID:reminder-${rem.reminderId}@habittracker")
        line(sb, "DTSTAMP:$stamp")
        line(sb, "DTSTART:${LOCAL.format(start)}")
        line(sb, "DTEND:${LOCAL.format(start.plusMinutes(15))}")
        val rule = if (rem.days.isEmpty()) "FREQ=DAILY"
        else "FREQ=WEEKLY;BYDAY=" + rem.days.sorted().joinToString(",") { BYDAY[it - 1] }
        line(sb, "RRULE:$rule")
        line(sb, "SUMMARY:${escape(rem.habitName)}")
        line(sb, "END:VEVENT")
    }

    private fun escape(text: String): String = text
        .replace("\\", "\\\\")
        .replace(";", "\\;")
        .replace(",", "\\,")
        .replace("\n", "\\n")

    /** Appends a content line, folding to 75 octets per RFC 5545 and terminating with CRLF. */
    private fun line(sb: StringBuilder, content: String) {
        var rest = content
        var limit = 75
        while (rest.length > limit) {
            sb.append(rest, 0, limit).append("\r\n ")
            rest = rest.substring(limit)
            limit = 74 // continuation lines start with a leading space
        }
        sb.append(rest).append("\r\n")
    }
}
