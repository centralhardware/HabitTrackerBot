import dto.CheckinEventValues
import dto.CheckinStatus
import dto.CheckinValueRow
import dto.FieldValue
import java.time.LocalDate

/**
 * Pure computations over a track's raw check-in rows ([CheckinValueRow]), loaded once via
 * `CheckInRepository.loadForTrack`. This replaces the former specialized aggregate SQL queries:
 * the database just returns rows, all counting/summing lives here in Kotlin.
 *
 * Conventions mirror the old SQL: manual events have `isScheduled == false`; counter events
 * carry a null quantity, quantity events a non-null one; scheduled events count toward "logged"
 * only when their status is `done`.
 */
object CheckinAnalytics {

    /** Earliest check-in date, or null when there is no history. */
    fun firstDate(rows: List<CheckinValueRow>): LocalDate? =
        rows.minOfOrNull { it.date }

    /** Per-day sums of manual quantities (old `quantitySumsPerDay`). Timer annotation fields are
     *  pure notes — excluded so their numbers never inflate a timer's logged time. */
    fun quantitySumsPerDay(rows: List<CheckinValueRow>): Map<LocalDate, Double> =
        rows.filter { !it.isScheduled && it.quantity != null && it.timerPhase == null }
            .groupBy { it.date }
            .mapValues { (_, day) -> day.sumOf { it.quantity!! } }

    /** Days with any logged activity: manual events, or scheduled events marked done.
     *  Timer annotation fields don't make a day "logged" on their own. */
    fun loggedDates(rows: List<CheckinValueRow>): Set<LocalDate> =
        rows.filter { (!it.isScheduled || it.status == CheckinStatus.DONE) && it.timerPhase == null }
            .mapTo(mutableSetOf()) { it.date }

    /** Days with a skipped scheduled event. */
    fun skipDates(rows: List<CheckinValueRow>): Set<LocalDate> =
        rows.filter { it.status == CheckinStatus.SKIP }
            .mapTo(mutableSetOf()) { it.date }

    /**
     * Rows in [from]..[to] mapped to the public [CheckinRecord] shape (old `findInRange`).
     * When [isTimer] is true, the duration row also carries a derived [CheckinRecord.startedAt]
     * (recordedAt − elapsed seconds) so the session's start as well as its end are exposed.
     */
    fun inRange(rows: List<CheckinValueRow>, from: LocalDate, to: LocalDate, isTimer: Boolean = false): List<CheckinEventValues> =
        rows.filter { it.date in from..to }
            // One record per event: a multi-field event's rows share checkinId, so gather their
            // per-param values into one map instead of repeating date/comment/recordedAt per param.
            .groupBy { it.checkinId }
            .map { (checkinId, group) ->
                val head = group.first()
                val values = group.mapNotNull { row ->
                    val pid = row.paramId ?: return@mapNotNull null
                    val value = row.quantity?.let { FieldValue.Numeric(it) } ?: row.textValue?.let { FieldValue.Text(it) }
                    value?.let { pid to it }
                }.toMap()
                // A timer's duration row (the elapsed-seconds value, not a before/after annotation):
                // start = end − seconds. Only there does deriving a start make sense.
                val startedAt = group.firstNotNullOfOrNull { row ->
                    row.recordedAt?.takeIf {
                        isTimer && !row.isScheduled && row.timerPhase == null && row.quantity != null
                    }?.minusSeconds(row.quantity!!.toLong())
                }
                CheckinEventValues(
                    checkinId = checkinId,
                    date = head.date,
                    // status is meaningful only for scheduled tracks; never surface it for quantity/counter rows.
                    status = head.status.takeIf { head.isScheduled },
                    values = values,
                    offsetMinutes = head.offsetMinutes,
                    comment = head.comment,
                    recordedAt = head.recordedAt,
                    startedAt = startedAt,
                )
            }
}
