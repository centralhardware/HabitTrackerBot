package services

import db.CheckInRepository
import db.TrackRepository
import dto.CheckinEvent
import dto.CheckinRecord
import dto.CheckinStatus
import dto.CheckinValue
import dto.CheckinValueRow
import dto.DeletableCheckin
import dto.Direction
import dto.FieldValue
import dto.Track
import dto.TrackParam
import dto.TrackCheckinPage
import dto.TrackStat
import dto.TrackStatus
import dto.TrackType
import dto.ParamType
import dto.QuantityTrend
import org.apache.commons.math3.stat.descriptive.moment.Mean
import java.time.LocalDate
import java.time.temporal.ChronoUnit

object CheckInService {

    fun record(reminderId: Long, userId: Long, date: LocalDate, status: CheckinStatus): Boolean {
        val trackId = TrackRepository.findTrackIdByReminder(reminderId, userId) ?: return false
        return CheckInRepository.upsertScheduledValue(
            CheckinEvent(userId, date, reminderId, trackId, comment = null),
            status,
        )
    }

    fun checkInCounter(trackId: Long, userId: Long, date: LocalDate, comment: String? = null): Boolean {
        val track = TrackService.findById(trackId, userId) ?: return false
        if (track.type != TrackType.CHECK || !track.allowAdHoc) return false
        // An ad-hoc check-in is just a bare checkins row — no param, status or value to store.
        return CheckInRepository.insertEvent(
            CheckinEvent(userId, date, reminderId = null, trackId = trackId, comment = comment?.trim()?.ifEmpty { null }),
        ) > 0
    }

    fun recordQuantity(
        trackId: Long,
        userId: Long,
        date: LocalDate,
        values: Map<Long, FieldValue> = emptyMap(),
        comment: String? = null
    ): Long {
        if (values.isEmpty()) return 0
        val track = TrackService.findById(trackId, userId) ?: return 0
        if (track.type != TrackType.QUANTITY) return 0
        val valueRows = track.params.mapNotNull { p ->
            values[p.id]?.let { CheckinValue(p.id, CheckinStatus.DONE, it) }
        }
        if (valueRows.isEmpty()) return 0
        return CheckInRepository.insertEventWithValues(
            CheckinEvent(userId, date, reminderId = null, trackId = trackId, comment = comment),
            valueRows,
        )
    }

    /**
     * Records [seconds] of elapsed time as a check-in for a timer track, stored on its single
     * NUMBER param. Returns the new `checkins.id`, or 0 if the track isn't a timer / has no param.
     */
    fun recordTimer(trackId: Long, userId: Long, date: LocalDate, seconds: Double, comment: String? = null): Long {
        if (seconds <= 0.0) return 0
        val track = TrackService.findById(trackId, userId) ?: return 0
        if (track.type != TrackType.TIMER) return 0
        // The elapsed time lives on the duration param (a NUMBER param with no timer phase);
        // extra annotation fields are skipped here and attached separately.
        val param = track.params.firstOrNull { it.timerPhase == null && it.paramType == ParamType.NUMBER }
            ?: track.params.firstOrNull() ?: return 0
        return CheckInRepository.insertEventWithValues(
            CheckinEvent(userId, date, reminderId = null, trackId = trackId, comment = comment?.trim()?.ifEmpty { null }),
            listOf(CheckinValue(param.id, CheckinStatus.DONE, FieldValue.Numeric(seconds))),
        )
    }

    /** Sets (or clears) the comment on an existing event — used to attach a note to a stopped timer. */
    fun setComment(checkinId: Long, userId: Long, comment: String?): Boolean =
        CheckInRepository.updateCheckinComment(checkinId, userId, comment?.trim()?.ifEmpty { null })

    /**
     * Attaches a timer's extra annotation fields ([values]: paramId → entered text) to the
     * elapsed-time check-in [checkinId]. Only params that actually belong to the track are kept;
     * each value is stored verbatim (numbers as their numeric text, free text as-is) and never
     * counts toward any statistic.
     */
    fun attachTimerFieldValues(checkinId: Long, userId: Long, trackId: Long, values: Map<Long, String>) {
        if (checkinId <= 0 || values.isEmpty()) return
        val track = TrackService.findById(trackId, userId) ?: return
        val paramIds = track.params.mapTo(mutableSetOf()) { it.id }
        val rows = values.mapNotNull { (paramId, text) ->
            val clean = text.trim()
            if (paramId !in paramIds || clean.isEmpty()) null
            else CheckinValue(paramId, CheckinStatus.DONE, FieldValue.Text(clean))
        }
        CheckInRepository.addValues(checkinId, rows)
    }

    fun deleteCheckin(checkinId: Long, userId: Long, notBefore: LocalDate): DeleteOutcome {
        val event = CheckInRepository.loadEventForDelete(checkinId, userId) ?: return DeleteOutcome.NotFound
        if (event.date.isBefore(notBefore)) return DeleteOutcome.TooOld(event.date)
        return if (CheckInRepository.softDeleteEvent(checkinId, userId, notBefore)) DeleteOutcome.Deleted(event)
        else DeleteOutcome.NotFound
    }

    /** Outcome of a [deleteCheckin] attempt — distinguished so the caller can explain failures. */
    sealed interface DeleteOutcome {
        data class Deleted(val checkin: DeletableCheckin) : DeleteOutcome
        data object NotFound : DeleteOutcome
        data class TooOld(val date: LocalDate) : DeleteOutcome
    }

    /**
     * Patches a quantity check-in: optionally updates the comment and/or individual param quantities.
     * Only tracks dated on or after [notBefore] may be edited.
     * [updateComment] must be true to touch the comment field (allows setting it to null).
     * [valuePatch] maps paramId → new quantity (only listed params are updated).
     */
    fun updateCheckin(
        checkinId: Long,
        userId: Long,
        notBefore: LocalDate,
        updateComment: Boolean,
        comment: String?,
        valuePatch: Map<Long, String> = emptyMap(),
    ): UpdateOutcome {
        val event = CheckInRepository.loadEventForDelete(checkinId, userId) ?: return UpdateOutcome.NotFound
        if (event.date.isBefore(notBefore)) return UpdateOutcome.TooOld(event.date)
        // Validate against the track's params, not just the values already on the track: a patch may
        // set a param the track doesn't carry yet (e.g. a book name never filled in at the time), so
        // we upsert it rather than silently dropping it.
        val trackParamIds = TrackService.findById(event.trackId, userId)?.params?.mapTo(mutableSetOf()) { it.id }
            ?: return UpdateOutcome.NotFound
        if (updateComment) CheckInRepository.updateCheckinComment(checkinId, userId, comment)
        for ((paramId, value) in valuePatch) {
            if (paramId !in trackParamIds) continue
            CheckInRepository.upsertCheckinValue(checkinId, userId, paramId, value)
        }
        // `event` is the pre-patch snapshot (loaded above); reload for the post-patch state so the
        // caller can render a before→after diff rather than just the new values.
        return UpdateOutcome.Updated(before = event, checkin = CheckInRepository.loadEventForDelete(checkinId, userId) ?: event)
    }

    sealed interface UpdateOutcome {
        data class Updated(val before: DeletableCheckin, val checkin: DeletableCheckin) : UpdateOutcome
        data object NotFound : UpdateOutcome
        data class TooOld(val date: LocalDate) : UpdateOutcome
    }

    /** A page of recent check-ins plus whether a further (older) page exists. */
    data class RecentPage(val items: List<dto.RecentCheckin>, val hasNext: Boolean, val page: Int)

    /** Loads page [page] (0-based) of the user's recent check-ins, newest first, [pageSize] per page. */
    fun recentCheckins(userId: Long, page: Int, pageSize: Int): RecentPage {
        val safePage = page.coerceAtLeast(0)
        // Fetch one extra row to detect whether an older page follows, then drop it.
        val rows = CheckInRepository.loadRecentForUser(userId, pageSize + 1, safePage * pageSize)
        return RecentPage(rows.take(pageSize), rows.size > pageSize, safePage)
    }

    fun listInRange(trackId: Long, userId: Long, from: LocalDate, to: LocalDate): TrackCheckinPage? {
        val track = TrackService.findById(trackId, userId) ?: return null
        val events = CheckinAnalytics.inRange(
            CheckInRepository.loadForTrack(trackId), from, to, isTimer = track.type == TrackType.TIMER,
        )
        // Intern values per track: a value repeated across check-ins gets one id, spelled out once
        // in valueDict, so the (potentially long) text isn't echoed in every check-in that uses it.
        val ids = LinkedHashMap<FieldValue, Int>()
        val checkins = events.map { e ->
            CheckinRecord(
                checkinId = e.checkinId,
                date = e.date,
                status = e.status,
                values = e.values.mapValues { (_, v) -> ids.getOrPut(v) { ids.size + 1 } },
                offsetMinutes = e.offsetMinutes,
                comment = e.comment,
                recordedAt = e.recordedAt,
                startedAt = e.startedAt,
            )
        }
        return TrackCheckinPage(checkins, ids.entries.associate { (v, id) -> id to v })
    }

    /** Number of counter/manual events logged for [trackId] on [date]. */
    fun counterCountOn(trackId: Long, date: LocalDate): Int =
        CheckinAnalytics.countOn(CheckInRepository.loadForTrack(trackId), date)

    /** The id of the most recent check-in for [trackId]/[userId], or 0 if none. */
    fun latestCheckin(trackId: Long, userId: Long): Long =
        CheckInRepository.latestCheckin(trackId, userId)

    /** Total seconds recorded for a timer [trackId] on [date]. */
    fun timerSecondsOn(trackId: Long, date: LocalDate): Double =
        CheckinAnalytics.quantitySumsPerDay(CheckInRepository.loadForTrack(trackId))[date] ?: 0.0

    fun userStats(userId: Long, today: LocalDate): List<TrackStat> {
        // Log-only tracks are pure journals — normally omitted — but timers track elapsed time
        // worth surfacing, so a log-only timer still shows up (just its recorded time, no verdict).
        // Paused tracks are excluded too: listActive() really returns all non-deleted tracks.
        return TrackService.listActive(userId)
            .filter { it.status == TrackStatus.ACTIVE && (!it.logOnly || it.type == TrackType.TIMER) }
            .map { trackStat(it, today) }
    }

    private fun trackStat(h: Track, today: LocalDate): TrackStat {
        val rows = CheckInRepository.loadForTrack(h.id)
        val loggedDates = CheckinAnalytics.loggedDates(rows)
        val skipDates = CheckinAnalytics.skipDates(rows)
        val pastLogged = loggedDates.count { it < today }
        return TrackStat(
            trackId = h.id,
            name = h.name,
            streak = streak(loggedDates, skipDates, today),
            loggedDays = pastLogged,
            totalDays = pastDaysSince(rows, today),
            trend = if ((h.type == TrackType.QUANTITY || h.type == TrackType.TIMER) && !h.multiField)
                quantityTrend(h.unit, h.direction, rows, today, target = h.dailyTarget, isDuration = h.type == TrackType.TIMER)
            else null,
            groupFields = if (h.multiField) h.params.map { paramStat(h, it, rows, today) } else emptyList(),
            logOnly = h.logOnly,
        )
    }

    /** Per-param sub-stat of a multi-field quantity track, over rows of just that param. */
    private fun paramStat(h: Track, p: TrackParam, allRows: List<CheckinValueRow>, today: LocalDate): TrackStat {
        val rows = allRows.filter { it.paramId == p.id }
        val loggedDates = CheckinAnalytics.loggedDates(rows)
        val skipDates = CheckinAnalytics.skipDates(rows)
        return TrackStat(
            trackId = h.id,
            name = p.name ?: h.name,
            streak = streak(loggedDates, skipDates, today),
            loggedDays = loggedDates.count { it < today },
            totalDays = pastDaysSince(rows, today),
            // Text params have no quantity — a numeric trend over them is meaningless (always 0/0/0).
            trend = if (p.paramType == ParamType.NUMBER) quantityTrend(p.unit, p.direction, rows, today) else null,
        )
    }

    private fun pastDaysSince(rows: List<CheckinValueRow>, today: LocalDate): Int {
        val start = CheckinAnalytics.firstDate(rows) ?: return 0
        return ChronoUnit.DAYS.between(start, today).toInt().coerceAtLeast(0)
    }

    private fun streak(logged: Set<LocalDate>, skipped: Set<LocalDate>, today: LocalDate): Int {
        if (today in skipped) return 0
        val good: (LocalDate) -> Boolean = { it in logged && it !in skipped }
        var d = today.minusDays(1)
        var streak = 0
        while (good(d)) {
            streak++
            d = d.minusDays(1)
        }
        return streak
    }

    private const val TREND_WINDOW_DAYS = 7

    private fun quantityTrend(
        unit: String?,
        direction: Direction?,
        rows: List<CheckinValueRow>,
        today: LocalDate,
        target: Double? = null,
        isDuration: Boolean = false,
    ): QuantityTrend {
        val perDay = CheckinAnalytics.quantitySumsPerDay(rows)
        val recent = (1..TREND_WINDOW_DAYS)
            .mapNotNull { perDay[today.minusDays(it.toLong())] }
            .toDoubleArray()
        val all = perDay.filterKeys { it < today }.values.toDoubleArray()
        return QuantityTrend(
            unit = unit,
            direction = direction,
            today = perDay[today] ?: 0.0,
            recentAvg = if (recent.isEmpty()) 0.0 else Mean().evaluate(recent),
            overallAvg = if (all.isEmpty()) 0.0 else Mean().evaluate(all),
            windowDays = TREND_WINDOW_DAYS,
            target = target,
            isDuration = isDuration,
        )
    }

    /**
     * Marks a scheduled slot pending and skips any older still-pending check-in of the same
     * track (the previous reminder occurrence the user never resolved). Returns the flipped
     * ones, for message updates.
     */
    fun markPending(trackId: Long, userId: Long, reminderId: Long, date: LocalDate): List<dto.ResolvedCheckin> {
        return CheckInRepository.markPendingSkippingPrevious(
            CheckinEvent(userId, date, reminderId, trackId, comment = null),
        )
    }
}
