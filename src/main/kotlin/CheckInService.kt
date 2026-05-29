import db.CheckInRepository
import db.HabitRepository
import dto.CheckinEvent
import dto.CheckinRecord
import dto.CheckinStatus
import dto.CheckinValue
import dto.Habit
import dto.HabitStat
import dto.HabitStatus
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

    fun recordQuantity(habitId: Long, userId: Long, date: LocalDate, value: Double, comment: String? = null): Boolean {
        val habit = HabitService.findById(habitId, userId) ?: return false
        if (habit.type != HabitType.QUANTITY || habit.isGroupRoot) return false
        return CheckInRepository.insertEventWithValues(
            CheckinEvent(userId, date, reminderId = null, comment = comment),
            listOf(CheckinValue(habitId, CheckinStatus.DONE, quantity = value)),
        ) > 0
    }

    /**
     * Записывает событие чекина группы: одну строку в checkins (с общим комментом)
     * и N строк в checkin_values (по полю). Возвращает количество записанных строк.
     */
    fun recordQuantityGroup(
        rootId: Long,
        userId: Long,
        date: LocalDate,
        values: Map<Long, Double>,
        comment: String? = null
    ): Int {
        if (values.isEmpty()) return 0
        val root = HabitService.findById(rootId, userId) ?: return 0
        if (!root.isGroupRoot) return 0
        val allowedFieldIds = root.fields.map { it.id }.toSet()
        val sanitized = values.filterKeys { it in allowedFieldIds }
        if (sanitized.isEmpty()) return 0
        val valueRows = sanitized.map { (fieldId, value) ->
            CheckinValue(fieldId, CheckinStatus.DONE, quantity = value)
        }
        return CheckInRepository.insertEventWithValues(
            CheckinEvent(userId, date, reminderId = null, comment = comment),
            valueRows,
        )
    }

    fun listInRange(habitId: Long, userId: Long, from: LocalDate, to: LocalDate): List<CheckinRecord>? {
        HabitService.findById(habitId, userId) ?: return null
        return CheckInRepository.findInRange(habitId, from, to)
    }

    fun userStats(userId: Long, today: LocalDate): List<HabitStat> {
        return HabitService.listActive(userId)
            .filter { it.status == HabitStatus.ACTIVE }
            .map { habitStat(it, today) }
    }

    private fun habitStat(h: Habit, today: LocalDate): HabitStat {
        val loggedDates = collectDates(h, CheckInRepository::loggedDates)
        val skipDates = collectDates(h, CheckInRepository::skipDates)
        val pastLogged = loggedDates.count { it < today }
        return HabitStat(
            habitId = h.id,
            name = h.name,
            streak = streak(loggedDates, skipDates, today),
            loggedDays = pastLogged,
            totalDays = pastDaysSince(h, today),
            trend = if (h.type == HabitType.QUANTITY && !h.isGroupRoot) quantityTrend(h, today) else null,
            groupFields = if (h.isGroupRoot) h.fields.map { habitStat(it, today) } else emptyList(),
        )
    }

    private fun pastDaysSince(h: Habit, today: LocalDate): Int {
        val start = firstCheckinDate(h) ?: return 0
        return ChronoUnit.DAYS.between(start, today).toInt().coerceAtLeast(0)
    }

    private fun firstCheckinDate(h: Habit): LocalDate? {
        if (h.isGroupRoot) {
            return h.fields.mapNotNull { CheckInRepository.firstCheckinDate(it.id) }.minOrNull()
        }
        return CheckInRepository.firstCheckinDate(h.id)
    }

    private fun collectDates(h: Habit, query: (Long) -> List<LocalDate>): Set<LocalDate> {
        if (h.isGroupRoot) {
            val all = mutableSetOf<LocalDate>()
            h.fields.forEach { all += query(it.id) }
            return all
        }
        return query(h.id).toSet()
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

    private fun quantityTrend(h: Habit, today: LocalDate): QuantityTrend {
        val perDay = CheckInRepository.quantitySumsPerDay(h.id).associate { it.date to it.amount }
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
