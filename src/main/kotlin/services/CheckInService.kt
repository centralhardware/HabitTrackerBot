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
import dto.Habit
import dto.HabitParam
import dto.HabitStat
import dto.HabitType
import dto.QuantityTrend
import org.apache.commons.math3.stat.descriptive.moment.Mean
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

object CheckInService {

    fun record(reminderId: Long, userId: Long, date: LocalDate, status: CheckinStatus): Boolean {
        val habitId = HabitRepository.findHabitIdByReminder(reminderId, userId) ?: return false
        val paramId = HabitRepository.firstParamId(habitId, userId) ?: return false
        return CheckInRepository.upsertScheduledValue(
            CheckinEvent(userId, date, reminderId, habitId, comment = null),
            CheckinValue(paramId, status, quantity = null),
        )
    }

    fun checkInCounter(habitId: Long, userId: Long, date: LocalDate, comment: String? = null): Boolean {
        val habit = HabitService.findById(habitId, userId) ?: return false
        if (habit.type != HabitType.COUNTER) return false
        val paramId = habit.params.firstOrNull()?.id ?: return false
        return CheckInRepository.insertEventWithValues(
            CheckinEvent(userId, date, reminderId = null, habitId = habitId, comment = comment?.trim()?.ifEmpty { null }),
            listOf(CheckinValue(paramId, CheckinStatus.DONE, quantity = null)),
        ) > 0
    }

    /**
     * Записывает событие чекина quantity-привычки. Принимает числовые значения ([numericValues],
     * paramId → Double) и текстовые ([textValues], paramId → String) отдельно.
     * Возвращает id созданного события (`checkins.id`), либо 0, если записать нечего.
     */
    fun recordQuantity(
        habitId: Long,
        userId: Long,
        date: LocalDate,
        numericValues: Map<Long, Double> = emptyMap(),
        textValues: Map<Long, String> = emptyMap(),
        comment: String? = null
    ): Long {
        if (numericValues.isEmpty() && textValues.isEmpty()) return 0
        val habit = HabitService.findById(habitId, userId) ?: return 0
        if (habit.type != HabitType.QUANTITY) return 0
        val allowedIds = habit.params.map { it.id }.toSet()
        val valueRows = habit.params.mapNotNull { p ->
            when {
                p.id in numericValues && p.id in allowedIds ->
                    CheckinValue(p.id, CheckinStatus.DONE, quantity = numericValues[p.id]!!)
                p.id in textValues && p.id in allowedIds ->
                    CheckinValue(p.id, CheckinStatus.DONE, quantity = null, textValue = textValues[p.id])
                else -> null
            }
        }
        if (valueRows.isEmpty()) return 0
        return CheckInRepository.insertEventWithValues(
            CheckinEvent(userId, date, reminderId = null, habitId = habitId, comment = comment),
            valueRows,
        )
    }

    /**
     * Soft-deletes a quantity check-in event (and, with it, all of its values at once), scoped to
     * [userId]. Only events dated on or after [notBefore] may be deleted — typically yesterday, so
     * mistakes from today or yesterday can be retracted while older history stays protected.
     */
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

    fun userStats(userId: Long, today: LocalDate): List<HabitStat> {
        // Log-only habits are pure journals — no streaks/completion/trends — so they're omitted here.
        return HabitService.listActive(userId).filterNot { it.logOnly }.map { habitStat(it, today) }
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
            trend = if (h.type == HabitType.QUANTITY && !h.multiField) quantityTrend(h.unit, h.direction, rows, today) else null,
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
            trend = quantityTrend(p.unit, p.direction, rows, today),
        )
    }

    private fun pastDaysSince(rows: List<CheckinValueRow>, today: LocalDate): Int {
        val start = CheckinAnalytics.firstDate(rows) ?: return 0
        return ChronoUnit.DAYS.between(start, today).toInt().coerceAtLeast(0)
    }

    private fun streak(logged: Set<LocalDate>, skipped: Set<LocalDate>, today: LocalDate): Int {
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
        val paramId = HabitRepository.firstParamId(habitId, userId) ?: return
        CheckInRepository.upsertScheduledValue(
            CheckinEvent(userId, date, reminderId, habitId, comment = null),
            CheckinValue(paramId, status = null, quantity = null),
        )
    }
}
