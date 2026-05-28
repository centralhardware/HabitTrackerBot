import db.CheckInRepository
import db.HabitRepository
import dto.Checkin
import dto.CheckinRecord
import dto.CheckinStatus
import dto.Habit
import dto.HabitStat
import dto.HabitType
import dto.QuantityTrend
import org.apache.commons.math3.stat.descriptive.moment.Mean
import org.apache.commons.math3.stat.regression.SimpleRegression
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

object CheckInService {

    fun record(reminderId: Long, userId: Long, date: LocalDate, status: CheckinStatus): Boolean {
        val habitId = HabitRepository.findHabitIdByReminder(reminderId, userId) ?: return false
        return CheckInRepository.upsert(Checkin(habitId, reminderId, date, status, quantity = null))
    }

    fun checkInCounter(habitId: Long, userId: Long, date: LocalDate): Boolean {
        val habit = HabitService.findById(habitId, userId) ?: return false
        if (habit.type != HabitType.COUNTER) return false
        return CheckInRepository.upsert(Checkin(habitId, reminderId = null, date, CheckinStatus.DONE, quantity = null))
    }

    fun recordQuantity(habitId: Long, userId: Long, date: LocalDate, value: Double, comment: String? = null): Boolean {
        val habit = HabitService.findById(habitId, userId) ?: return false
        if (habit.type != HabitType.QUANTITY || habit.isGroupRoot) return false
        val commentId = comment?.let { CheckInRepository.createComment(it) }
        return CheckInRepository.upsert(
            Checkin(habitId, reminderId = null, date, CheckinStatus.DONE, quantity = value, commentId = commentId)
        )
    }

    /**
     * Записывает чек-ин-событие группы: одну строку в comments (общий коммент)
     * и N строк checkins (по полю). Возвращает количество записанных строк.
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
        val commentId = comment?.let { CheckInRepository.createComment(it) }
        var wrote = 0
        sanitized.forEach { (fieldId, value) ->
            val ok = CheckInRepository.upsert(
                Checkin(fieldId, reminderId = null, date, CheckinStatus.DONE, quantity = value, commentId = commentId)
            )
            if (ok) wrote++
        }
        return wrote
    }

    fun listInRange(habitId: Long, userId: Long, from: LocalDate, to: LocalDate): List<CheckinRecord>? {
        HabitService.findById(habitId, userId) ?: return null
        return CheckInRepository.findInRange(habitId, from, to)
    }

    fun userStats(userId: Long, today: LocalDate): List<HabitStat> {
        return HabitService.listActive(userId).map { habitStat(it, today) }
    }

    private fun habitStat(h: Habit, today: LocalDate): HabitStat {
        val loggedDates = collectDates(h, CheckInRepository::loggedDates)
        val skipDates = collectDates(h, CheckInRepository::skipDates)
        val totalDays = totalDaysSince(h, today)
        return HabitStat(
            habitId = h.id,
            name = h.name,
            streak = streak(loggedDates, skipDates, today),
            loggedDays = loggedDates.size,
            totalDays = totalDays,
            trend = if (h.type == HabitType.QUANTITY && !h.isGroupRoot) quantityTrend(h, today) else null,
            groupFields = if (h.isGroupRoot) h.fields.map { habitStat(it, today) } else emptyList(),
        )
    }

    private fun totalDaysSince(h: Habit, today: LocalDate): Int {
        val start = CheckInRepository.habitStartDate(h.id) ?: today
        return (ChronoUnit.DAYS.between(start, today) + 1).toInt().coerceAtLeast(0)
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
        val anchor = when {
            good(today) -> today
            good(today.minusDays(1)) -> today.minusDays(1)
            else -> return 0
        }
        var streak = 0
        var d = anchor
        while (good(d)) {
            streak++
            d = d.minusDays(1)
        }
        return streak
    }

    private fun quantityTrend(h: Habit, today: LocalDate): QuantityTrend {
        val perDay = CheckInRepository.quantitySumsPerDay(h.id).associate { it.date to it.amount }
        val todayVal = perDay[today] ?: 0.0
        val week = window(perDay, today, 0..6)
        val avg7 = if (week.isEmpty()) 0.0 else Mean().evaluate(week)
        val slope14 = linearSlope(perDay, today, 13)
        return QuantityTrend(
            unit = h.unit,
            direction = h.direction,
            today = todayVal,
            avg7 = avg7,
            slope14 = slope14,
        )
    }

    private fun window(perDay: Map<LocalDate, Double>, today: LocalDate, offsets: IntRange): DoubleArray =
        offsets.map { perDay[today.minusDays(it.toLong())] ?: 0.0 }.toDoubleArray()

    private fun linearSlope(perDay: Map<LocalDate, Double>, today: LocalDate, daysBack: Int): Double {
        val reg = SimpleRegression()
        for (i in 0..daysBack) {
            val v = perDay[today.minusDays(i.toLong())] ?: 0.0
            reg.addData((daysBack - i).toDouble(), v)
        }
        return if (reg.n >= 2 && !reg.slope.isNaN()) reg.slope else 0.0
    }

    fun autoSkipOverdue() {
        val threshold = Instant.now().minus(Duration.ofHours(24))
        CheckInRepository.markPendingAsSkip(threshold)
    }

    fun markPending(habitId: Long, reminderId: Long, date: LocalDate) {
        CheckInRepository.upsert(Checkin(habitId, reminderId, date, status = null, quantity = null))
    }
}
