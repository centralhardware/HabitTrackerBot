package services

import db.CheckInRepository
import db.HabitRepository
import dto.CheckinEvent
import dto.CheckinRecord
import dto.CheckinStatus
import dto.CheckinValue
import dto.CheckinValueRow
import dto.DeletableCheckin
import dto.Direction
import dto.FieldValue
import dto.Habit
import dto.HabitParam
import dto.HabitStat
import dto.HabitStatus
import dto.HabitType
import dto.ParamType
import dto.QuantityTrend
import org.apache.commons.math3.stat.descriptive.moment.Mean
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

object CheckInService {

    fun record(reminderId: Long, userId: Long, date: LocalDate, status: CheckinStatus): Boolean {
        val habitId = HabitRepository.findHabitIdByReminder(reminderId, userId) ?: return false
        return CheckInRepository.upsertScheduledValue(
            CheckinEvent(userId, date, reminderId, habitId, comment = null),
            status,
        )
    }

    fun checkInCounter(habitId: Long, userId: Long, date: LocalDate, comment: String? = null): Boolean {
        val habit = HabitService.findById(habitId, userId) ?: return false
        if (habit.type != HabitType.COUNTER) return false
        // A counter event is just a bare checkins row — no param, status or value to store.
        return CheckInRepository.insertEvent(
            CheckinEvent(userId, date, reminderId = null, habitId = habitId, comment = comment?.trim()?.ifEmpty { null }),
        ) > 0
    }

    fun recordQuantity(
        habitId: Long,
        userId: Long,
        date: LocalDate,
        values: Map<Long, FieldValue> = emptyMap(),
        comment: String? = null
    ): Long {
        if (values.isEmpty()) return 0
        val habit = HabitService.findById(habitId, userId) ?: return 0
        if (habit.type != HabitType.QUANTITY) return 0
        val valueRows = habit.params.mapNotNull { p ->
            values[p.id]?.let { CheckinValue(p.id, CheckinStatus.DONE, it) }
        }
        if (valueRows.isEmpty()) return 0
        return CheckInRepository.insertEventWithValues(
            CheckinEvent(userId, date, reminderId = null, habitId = habitId, comment = comment),
            valueRows,
        )
    }

    /**
     * Records [seconds] of elapsed time as a check-in for a timer habit, stored on its single
     * NUMBER param. Returns the new `checkins.id`, or 0 if the habit isn't a timer / has no param.
     */
    fun recordTimer(habitId: Long, userId: Long, date: LocalDate, seconds: Double, comment: String? = null): Long {
        if (seconds <= 0.0) return 0
        val habit = HabitService.findById(habitId, userId) ?: return 0
        if (habit.type != HabitType.TIMER) return 0
        val param = habit.params.firstOrNull() ?: return 0
        return CheckInRepository.insertEventWithValues(
            CheckinEvent(userId, date, reminderId = null, habitId = habitId, comment = comment?.trim()?.ifEmpty { null }),
            listOf(CheckinValue(param.id, CheckinStatus.DONE, FieldValue.Numeric(seconds))),
        )
    }

    /** Sets (or clears) the comment on an existing event — used to attach a note to a stopped timer. */
    fun setComment(checkinId: Long, userId: Long, comment: String?): Boolean =
        CheckInRepository.updateCheckinComment(checkinId, userId, comment?.trim()?.ifEmpty { null })

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
     * Only entries dated on or after [notBefore] may be edited.
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
        val allowedParamIds = event.values.map { it.paramId }.toSet()
        if (updateComment) CheckInRepository.updateCheckinComment(checkinId, userId, comment)
        for ((paramId, value) in valuePatch) {
            if (paramId !in allowedParamIds) continue
            CheckInRepository.updateCheckinValue(checkinId, userId, paramId, value)
        }
        return UpdateOutcome.Updated(CheckInRepository.loadEventForDelete(checkinId, userId) ?: event)
    }

    sealed interface UpdateOutcome {
        data class Updated(val checkin: DeletableCheckin) : UpdateOutcome
        data object NotFound : UpdateOutcome
        data class TooOld(val date: LocalDate) : UpdateOutcome
    }

    fun listInRange(habitId: Long, userId: Long, from: LocalDate, to: LocalDate): List<CheckinRecord>? {
        HabitService.findById(habitId, userId) ?: return null
        return CheckinAnalytics.inRange(CheckInRepository.loadForHabit(habitId), from, to)
    }

    /** Number of counter/manual events logged for [habitId] on [date]. */
    fun counterCountOn(habitId: Long, date: LocalDate): Int =
        CheckinAnalytics.countOn(CheckInRepository.loadForHabit(habitId), date)

    /** Total seconds recorded for a timer [habitId] on [date]. */
    fun timerSecondsOn(habitId: Long, date: LocalDate): Double =
        CheckinAnalytics.quantitySumsPerDay(CheckInRepository.loadForHabit(habitId))[date] ?: 0.0

    fun userStats(userId: Long, today: LocalDate): List<HabitStat> {
        // Log-only habits are pure journals — no streaks/completion/trends — so they're omitted here.
        // Paused habits are excluded too: listActive() really returns all non-deleted habits.
        return HabitService.listActive(userId)
            .filter { it.status == HabitStatus.ACTIVE && !it.logOnly }
            .map { habitStat(it, today) }
    }

    private fun habitStat(h: Habit, today: LocalDate): HabitStat {
        val rows = CheckInRepository.loadForHabit(h.id)
        val loggedDates = CheckinAnalytics.loggedDates(rows)
        val skipDates = CheckinAnalytics.skipDates(rows)
        val pastLogged = loggedDates.count { it < today }
        return HabitStat(
            habitId = h.id,
            name = h.name,
            streak = streak(loggedDates, skipDates, today),
            loggedDays = pastLogged,
            totalDays = pastDaysSince(rows, today),
            trend = if ((h.type == HabitType.QUANTITY || h.type == HabitType.TIMER) && !h.multiField) quantityTrend(h.unit, h.direction, rows, today) else null,
            groupFields = if (h.multiField) h.params.map { paramStat(h, it, rows, today) } else emptyList(),
        )
    }

    /** Per-param sub-stat of a multi-field quantity habit, over rows of just that param. */
    private fun paramStat(h: Habit, p: HabitParam, allRows: List<CheckinValueRow>, today: LocalDate): HabitStat {
        val rows = allRows.filter { it.paramId == p.id }
        val loggedDates = CheckinAnalytics.loggedDates(rows)
        val skipDates = CheckinAnalytics.skipDates(rows)
        return HabitStat(
            habitId = h.id,
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

    private fun quantityTrend(unit: String?, direction: Direction?, rows: List<CheckinValueRow>, today: LocalDate): QuantityTrend {
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
        )
    }

    /** Skips pending scheduled check-ins older than 24h; returns the ones flipped, for message updates. */
    fun autoSkipOverdue(): List<dto.ResolvedCheckin> {
        val threshold = Instant.now().minus(Duration.ofHours(24))
        return CheckInRepository.markPendingAsSkip(threshold)
    }

    fun markPending(habitId: Long, userId: Long, reminderId: Long, date: LocalDate) {
        CheckInRepository.upsertScheduledValue(
            CheckinEvent(userId, date, reminderId, habitId, comment = null),
            CheckinStatus.PENDING,
        )
    }
}
