import dto.CheckinRecord
import dto.CheckinStatus
import dto.CheckinValueRow
import dto.WeekTotals
import java.time.LocalDate

/**
 * Pure computations over a habit's raw check-in rows ([CheckinValueRow]), loaded once via
 * `CheckInRepository.loadForHabit`. This replaces the former specialized aggregate SQL queries:
 * the database just returns rows, all counting/summing lives here in Kotlin.
 *
 * Conventions mirror the old SQL: manual events have `isScheduled == false`; counter events
 * carry a null quantity, quantity events a non-null one; scheduled events count toward "logged"
 * only when their status is `done`.
 */
object CheckinAnalytics {

    /** Number of counter/manual events on [date] (old `todayCounterCount`). */
    fun countOn(rows: List<CheckinValueRow>, date: LocalDate): Int =
        rows.count { !it.isScheduled && it.date == date }

    /** Earliest check-in date, or null when there is no history. */
    fun firstDate(rows: List<CheckinValueRow>): LocalDate? =
        rows.minOfOrNull { it.date }

    /** Per-day sums of manual quantities (old `quantitySumsPerDay`). */
    fun quantitySumsPerDay(rows: List<CheckinValueRow>): Map<LocalDate, Double> =
        rows.filter { !it.isScheduled && it.quantity != null }
            .groupBy { it.date }
            .mapValues { (_, day) -> day.sumOf { it.quantity!! } }

    /** Days with any logged activity: manual events, or scheduled events marked done. */
    fun loggedDates(rows: List<CheckinValueRow>): Set<LocalDate> =
        rows.filter { !it.isScheduled || it.status == CheckinStatus.DONE }
            .mapTo(mutableSetOf()) { it.date }

    /** Days with a skipped scheduled event. */
    fun skipDates(rows: List<CheckinValueRow>): Set<LocalDate> =
        rows.filter { it.status == CheckinStatus.SKIP }
            .mapTo(mutableSetOf()) { it.date }

    /** Rows in [from]..[to] mapped to the public [CheckinRecord] shape (old `findInRange`). */
    fun inRange(rows: List<CheckinValueRow>, from: LocalDate, to: LocalDate): List<CheckinRecord> =
        rows.filter { it.date in from..to }
            .map { CheckinRecord(it.checkinId, it.paramId, it.date, it.status, it.quantity, it.offsetMinutes, it.comment, it.textValue) }

    /** Weekly totals over [from]..[to] (old `WeeklySummaryRepository.weeklyTotals`). */
    fun weekTotals(rows: List<CheckinValueRow>, from: LocalDate, to: LocalDate): WeekTotals {
        val window = rows.filter { it.date in from..to }
        val counterEvents = window.filter { !it.isScheduled && it.quantity == null && it.textValue == null }
        val quantityEvents = window.filter { !it.isScheduled && it.quantity != null }
        return WeekTotals(
            done = window.count { it.isScheduled && it.status == CheckinStatus.DONE },
            skip = window.count { it.isScheduled && it.status == CheckinStatus.SKIP },
            total = counterEvents.size,
            days = counterEvents.mapTo(mutableSetOf()) { it.date }.size,
            quantityTotal = quantityEvents.sumOf { it.quantity!! },
            quantityDays = quantityEvents.mapTo(mutableSetOf()) { it.date }.size,
        )
    }

    /** Per-day counter-event counts within [from]..[to] (old `counterCountsInRange`). */
    fun counterCountsPerDay(rows: List<CheckinValueRow>, from: LocalDate, to: LocalDate): Map<LocalDate, Int> =
        rows.filter { !it.isScheduled && it.date in from..to }
            .groupingBy { it.date }
            .eachCount()

    /** Per-day quantity sums within [from]..[to] (old `quantitySumsInRange`). */
    fun quantitySumsPerDayInRange(rows: List<CheckinValueRow>, from: LocalDate, to: LocalDate): Map<LocalDate, Double> =
        rows.filter { !it.isScheduled && it.quantity != null && it.date in from..to }
            .groupBy { it.date }
            .mapValues { (_, day) -> day.sumOf { it.quantity!! } }
}
