import db.CheckInRepository
import db.HabitRepository
import dto.Checkin
import dto.CheckinRecord
import dto.CheckinStatus
import dto.Direction
import dto.Habit
import dto.HabitStat
import dto.HabitType
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

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
        if (habit.type != HabitType.QUANTITY) return false
        return CheckInRepository.upsert(
            Checkin(habitId, reminderId = null, date, CheckinStatus.DONE, quantity = value, comment = comment)
        )
    }

    fun listInRange(habitId: Long, userId: Long, from: LocalDate, to: LocalDate): List<CheckinRecord>? {
        HabitService.findById(habitId, userId) ?: return null
        return CheckInRepository.findInRange(habitId, from, to)
    }

    fun userStats(userId: Long, today: LocalDate): List<HabitStat> {
        val habits = HabitService.listActive(userId)
        return habits.map { h ->
            when {
                h.isGroupRoot -> quantityGroupStat(h, today)
                h.type == HabitType.SCHEDULED -> scheduledStat(h)
                h.type == HabitType.COUNTER -> counterStat(h, today)
                h.type == HabitType.QUANTITY -> quantityStat(h, today)
                else -> error("Unhandled habit ${h.id}")
            }
        }
    }

    private fun quantityGroupStat(root: dto.Habit, today: LocalDate): HabitStat.QuantityGroup {
        val fieldStats = root.fields.map { quantityStat(it, today) }
        val targetedFields = root.fields.filter { it.dailyTarget != null }
        val (doneDays, skipDays, streak) = if (targetedFields.isEmpty()) {
            Triple(0, 0, 0)
        } else {
            val startDate = CheckInRepository.habitStartDate(root.id) ?: today
            val perFieldDay: Map<Long, Map<LocalDate, Double>> = targetedFields.associate { f ->
                f.id to CheckInRepository.quantitySumsPerDay(f.id).associate { it.date to it.amount }
            }
            val dayDone: (LocalDate) -> Boolean = { d ->
                targetedFields.all { f ->
                    val v = perFieldDay[f.id]?.get(d) ?: 0.0
                    val target = f.dailyTarget!!
                    if (f.direction == Direction.LESS) v <= target else v >= target
                }
            }
            val (done, skip) = countHitsByPredicate(startDate, today.minusDays(1), dayDone)
            val s = streakFromAnchorByPredicate(startDate, today, dayDone)
            Triple(done, skip, s)
        }
        return HabitStat.QuantityGroup(
            habitId = root.id, name = root.name, fields = fieldStats,
            doneDays = doneDays, skipDays = skipDays, streak = streak
        )
    }

    private fun countHitsByPredicate(from: LocalDate, to: LocalDate, isDone: (LocalDate) -> Boolean): Pair<Int, Int> {
        if (to < from) return 0 to 0
        var done = 0
        var skip = 0
        var d = from
        while (d <= to) {
            if (isDone(d)) done++ else skip++
            d = d.plusDays(1)
        }
        return done to skip
    }

    private fun streakFromAnchorByPredicate(startDate: LocalDate, today: LocalDate, isDone: (LocalDate) -> Boolean): Int {
        val yesterday = today.minusDays(1)
        val anchor = when {
            isDone(today) -> today
            isDone(yesterday) -> yesterday
            else -> return 0
        }
        var streak = 0
        var d = anchor
        while (d >= startDate && isDone(d)) {
            streak++
            d = d.minusDays(1)
        }
        return streak
    }

    fun autoSkipOverdue() {
        val threshold = Instant.now().minus(Duration.ofHours(24))
        CheckInRepository.markPendingAsSkip(threshold)
    }

    fun markPending(habitId: Long, reminderId: Long, date: LocalDate) {
        CheckInRepository.upsert(Checkin(habitId, reminderId, date, status = null, quantity = null))
    }

    private fun scheduledStat(h: Habit): HabitStat.Scheduled {
        val totals = CheckInRepository.scheduledTotals(h.id)
        return HabitStat.Scheduled(
            habitId = h.id,
            name = h.name,
            totalDays = totals.totalDays,
            doneCount = totals.doneCount,
            skipCount = totals.skipCount,
            streak = currentStreak(h.id)
        )
    }

    private fun currentStreak(habitId: Long): Int {
        val days = CheckInRepository.findDailyDoneStatus(habitId)
        var streak = 0
        var expected: LocalDate? = null
        for (day in days) {
            if (expected == null) {
                if (!day.allDone) continue
                streak = 1
                expected = day.date.minusDays(1)
            } else {
                if (day.date != expected || !day.allDone) break
                streak++
                expected = day.date.minusDays(1)
            }
        }
        return streak
    }

    private fun counterStat(h: Habit, today: LocalDate): HabitStat.Counter {
        val target = h.dailyTarget?.toInt()
        val direction = h.direction
        return when {
            target != null -> counterWithTarget(h, today, target)
            direction != null -> counterTrend(h, today, direction)
            else -> counterPlain(h, today)
        }
    }

    private fun quantityStat(h: Habit, today: LocalDate): HabitStat.Quantity {
        val target = h.dailyTarget
        val direction = h.direction
        return when {
            target != null -> quantityWithTarget(h, today, target)
            direction != null -> quantityTrend(h, today, direction)
            else -> quantityPlain(h, today)
        }
    }

    private fun counterPlain(h: Habit, today: LocalDate): HabitStat.Counter.Plain {
        val all = CheckInRepository.counterCountsPerDay(h.id)
        val todayCount = all.firstOrNull { it.date == today }?.count ?: 0
        val total = all.sumOf { it.count }
        return HabitStat.Counter.Plain(h.id, h.name, todayCount, total, all.size)
    }

    private fun quantityPlain(h: Habit, today: LocalDate): HabitStat.Quantity.Plain {
        val all = CheckInRepository.quantitySumsPerDay(h.id)
        val todayTotal = all.firstOrNull { it.date == today }?.amount ?: 0.0
        val total = all.sumOf { it.amount }
        return HabitStat.Quantity.Plain(h.id, h.name, h.unit, todayTotal, total, all.size)
    }

    private fun counterWithTarget(h: Habit, today: LocalDate, target: Int): HabitStat.Counter.WithTarget {
        val perDay = CheckInRepository.counterCountsPerDay(h.id).associate { it.date to it.count }
        val startDate = CheckInRepository.habitStartDate(h.id) ?: today
        val isHit: (Int) -> Boolean = if (h.direction == Direction.LESS) { c -> c <= target } else { c -> c >= target }

        val todayCount = perDay[today] ?: 0
        val (doneDays, skipDays) = countHitsInRange(perDay, startDate, today.minusDays(1), isHit)
        val streak = streakFromAnchor(perDay, startDate, today, isHit)

        return HabitStat.Counter.WithTarget(
            habitId = h.id, name = h.name, dailyTarget = target, direction = h.direction,
            todayCount = todayCount, doneDays = doneDays, skipDays = skipDays, streak = streak
        )
    }

    private fun quantityWithTarget(h: Habit, today: LocalDate, target: Double): HabitStat.Quantity.WithTarget {
        val perDay = CheckInRepository.quantitySumsPerDay(h.id).associate { it.date to it.amount }
        val startDate = CheckInRepository.habitStartDate(h.id) ?: today
        val isHit: (Double) -> Boolean = if (h.direction == Direction.LESS) { a -> a <= target } else { a -> a >= target }

        val todayTotal = perDay[today] ?: 0.0
        val (doneDays, skipDays) = countHitsInRangeD(perDay, startDate, today.minusDays(1), isHit)
        val streak = streakFromAnchorD(perDay, startDate, today, isHit)

        return HabitStat.Quantity.WithTarget(
            habitId = h.id, name = h.name, unit = h.unit, dailyTarget = target, direction = h.direction,
            todayTotal = todayTotal, doneDays = doneDays, skipDays = skipDays, streak = streak
        )
    }

    private fun counterTrend(h: Habit, today: LocalDate, direction: Direction): HabitStat.Counter.Trend {
        val all = CheckInRepository.counterCountsPerDay(h.id)
        val perDay = all.associate { it.date to it.count }
        val total = all.sumOf { it.count }
        val daysLogged = all.size
        val byDay: (LocalDate) -> Double = { d -> (perDay[d] ?: 0).toDouble() }

        val todayCount = perDay[today] ?: 0
        val yesterdayCount = perDay[today.minusDays(1)] ?: 0
        val overallAvg = if (daysLogged > 0) total.toDouble() / daysLogged else 0.0

        return HabitStat.Counter.Trend(
            habitId = h.id, name = h.name, direction = direction,
            todayCount = todayCount, yesterdayCount = yesterdayCount,
            grandTotal = total, daysLogged = daysLogged, overallAvg = overallAvg,
            recent3Avg = avgWindow(byDay, today, 0..2),
            previous3Avg = avgWindow(byDay, today, 3..5),
            recent7Avg = avgWindow(byDay, today, 0..6),
            previous7Avg = avgWindow(byDay, today, 7..13)
        )
    }

    private fun quantityTrend(h: Habit, today: LocalDate, direction: Direction): HabitStat.Quantity.Trend {
        val all = CheckInRepository.quantitySumsPerDay(h.id)
        val perDay = all.associate { it.date to it.amount }
        val total = all.sumOf { it.amount }
        val daysLogged = all.size
        val byDay: (LocalDate) -> Double = { d -> perDay[d] ?: 0.0 }

        val todayTotal = perDay[today] ?: 0.0
        val yesterdayTotal = perDay[today.minusDays(1)] ?: 0.0
        val overallAvg = if (daysLogged > 0) total / daysLogged else 0.0

        return HabitStat.Quantity.Trend(
            habitId = h.id, name = h.name, unit = h.unit, direction = direction,
            todayTotal = todayTotal, yesterdayTotal = yesterdayTotal,
            grandTotal = total, daysLogged = daysLogged, overallAvg = overallAvg,
            recent3Avg = avgWindow(byDay, today, 0..2),
            previous3Avg = avgWindow(byDay, today, 3..5),
            recent7Avg = avgWindow(byDay, today, 0..6),
            previous7Avg = avgWindow(byDay, today, 7..13)
        )
    }

    private fun avgWindow(byDay: (LocalDate) -> Double, today: LocalDate, offsets: IntRange): Double {
        val values = offsets.map { byDay(today.minusDays(it.toLong())) }
        return if (values.isEmpty()) 0.0 else values.sum() / values.size
    }

    private fun countHitsInRange(
        counts: Map<LocalDate, Int>,
        from: LocalDate,
        to: LocalDate,
        isHit: (Int) -> Boolean
    ): Pair<Int, Int> {
        if (to < from) return 0 to 0
        var done = 0
        var skip = 0
        var d = from
        while (d <= to) {
            if (isHit(counts[d] ?: 0)) done++ else skip++
            d = d.plusDays(1)
        }
        return done to skip
    }

    private fun countHitsInRangeD(
        amounts: Map<LocalDate, Double>,
        from: LocalDate,
        to: LocalDate,
        isHit: (Double) -> Boolean
    ): Pair<Int, Int> {
        if (to < from) return 0 to 0
        var done = 0
        var skip = 0
        var d = from
        while (d <= to) {
            if (isHit(amounts[d] ?: 0.0)) done++ else skip++
            d = d.plusDays(1)
        }
        return done to skip
    }

    private fun streakFromAnchor(
        counts: Map<LocalDate, Int>,
        startDate: LocalDate,
        today: LocalDate,
        isHit: (Int) -> Boolean
    ): Int {
        val yesterday = today.minusDays(1)
        val anchor = when {
            isHit(counts[today] ?: 0) -> today
            isHit(counts[yesterday] ?: 0) -> yesterday
            else -> return 0
        }
        var streak = 0
        var d = anchor
        while (d >= startDate && isHit(counts[d] ?: 0)) {
            streak++
            d = d.minusDays(1)
        }
        return streak
    }

    private fun streakFromAnchorD(
        amounts: Map<LocalDate, Double>,
        startDate: LocalDate,
        today: LocalDate,
        isHit: (Double) -> Boolean
    ): Int {
        val yesterday = today.minusDays(1)
        val anchor = when {
            isHit(amounts[today] ?: 0.0) -> today
            isHit(amounts[yesterday] ?: 0.0) -> yesterday
            else -> return 0
        }
        var streak = 0
        var d = anchor
        while (d >= startDate && isHit(amounts[d] ?: 0.0)) {
            streak++
            d = d.minusDays(1)
        }
        return streak
    }
}
