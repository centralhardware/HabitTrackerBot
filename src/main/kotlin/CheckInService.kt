import db.CheckInRepository
import db.HabitRepository
import dto.CheckinEvent
import dto.CheckinRecord
import dto.CheckinStatus
import dto.CheckinValue
import dto.CheckinValueRow
import dto.Habit
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
        return CheckInRepository.upsertScheduledValue(
            CheckinEvent(userId, date, reminderId, comment = null),
            CheckinValue(habitId, status, quantity = null),
        )
    }

    fun checkInCounter(habitId: Long, userId: Long, date: LocalDate): Boolean {
        val habit = HabitService.findById(habitId, userId) ?: return false
        if (habit.type != HabitType.COUNTER) return false
        return CheckInRepository.insertEventWithValues(
            CheckinEvent(userId, date, reminderId = null, comment = null),
            listOf(CheckinValue(habitId, CheckinStatus.DONE, quantity = null)),
        ) > 0
    }

    /**
     * Записывает событие чекина quantity-привычки: одну строку в checkins (с общим комментом)
     * и N строк в checkin_values. [values] ключуется по id цели: для группы — по id полей,
     * для одиночной привычки — по её собственному id (одиночная = группа с одним значением).
     * Возвращает количество записанных строк.
     */
    fun recordQuantity(
        habitId: Long,
        userId: Long,
        date: LocalDate,
        values: Map<Long, Double>,
        comment: String? = null
    ): Int {
        if (values.isEmpty()) return 0
        val habit = HabitService.findById(habitId, userId) ?: return 0
        if (habit.type != HabitType.QUANTITY) return 0
        val allowedIds = if (habit.isGroupRoot) habit.fields.map { it.id }.toSet() else setOf(habit.id)
        val sanitized = values.filterKeys { it in allowedIds }
        if (sanitized.isEmpty()) return 0
        val valueRows = sanitized.map { (id, value) ->
            CheckinValue(id, CheckinStatus.DONE, quantity = value)
        }
        return CheckInRepository.insertEventWithValues(
            CheckinEvent(userId, date, reminderId = null, comment = comment),
            valueRows,
        )
    }

    fun listInRange(habitId: Long, userId: Long, from: LocalDate, to: LocalDate): List<CheckinRecord>? {
        HabitService.findById(habitId, userId) ?: return null
        return CheckinAnalytics.inRange(CheckInRepository.loadForHabit(habitId), from, to)
    }

    /** Number of counter/manual events logged for [habitId] on [date]. */
    fun counterCountOn(habitId: Long, date: LocalDate): Int =
        CheckinAnalytics.countOn(CheckInRepository.loadForHabit(habitId), date)

    /** Sum of manual quantities logged for [habitId] on [date]. */
    fun quantitySumOn(habitId: Long, date: LocalDate): Double =
        CheckinAnalytics.quantitySumOn(CheckInRepository.loadForHabit(habitId), date)

    fun userStats(userId: Long, today: LocalDate): List<HabitStat> {
        // Log-only habits are pure journals — no streaks/completion/trends — so they're omitted here.
        return HabitService.listActive(userId).filterNot { it.logOnly }.map { habitStat(it, today) }
    }

    private fun habitStat(h: Habit, today: LocalDate): HabitStat {
        val rows = loadRows(h)
        val loggedDates = CheckinAnalytics.loggedDates(rows)
        val skipDates = CheckinAnalytics.skipDates(rows)
        val pastLogged = loggedDates.count { it < today }
        return HabitStat(
            habitId = h.id,
            name = h.name,
            streak = streak(loggedDates, skipDates, today),
            loggedDays = pastLogged,
            totalDays = pastDaysSince(rows, today),
            trend = if (h.type == HabitType.QUANTITY && !h.isGroupRoot) quantityTrend(h, rows, today) else null,
            groupFields = if (h.isGroupRoot) h.fields.map { habitStat(it, today) } else emptyList(),
        )
    }

    /** Loads a habit's rows; for a group root, the union of its fields' rows. */
    private fun loadRows(h: Habit): List<CheckinValueRow> =
        if (h.isGroupRoot) h.fields.flatMap { CheckInRepository.loadForHabit(it.id) }
        else CheckInRepository.loadForHabit(h.id)

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

    private fun quantityTrend(h: Habit, rows: List<CheckinValueRow>, today: LocalDate): QuantityTrend {
        val perDay = CheckinAnalytics.quantitySumsPerDay(rows)
        val recent = (1..TREND_WINDOW_DAYS)
            .mapNotNull { perDay[today.minusDays(it.toLong())] }
            .toDoubleArray()
        val all = perDay.filterKeys { it < today }.values.toDoubleArray()
        return QuantityTrend(
            unit = h.unit,
            direction = h.direction,
            today = perDay[today] ?: 0.0,
            recentAvg = if (recent.isEmpty()) 0.0 else Mean().evaluate(recent),
            overallAvg = if (all.isEmpty()) 0.0 else Mean().evaluate(all),
            windowDays = TREND_WINDOW_DAYS,
        )
    }

    fun autoSkipOverdue() {
        val threshold = Instant.now().minus(Duration.ofHours(24))
        CheckInRepository.markPendingAsSkip(threshold)
    }

    fun markPending(habitId: Long, userId: Long, reminderId: Long, date: LocalDate) {
        CheckInRepository.upsertScheduledValue(
            CheckinEvent(userId, date, reminderId, comment = null),
            CheckinValue(habitId, status = null, quantity = null),
        )
    }
}
