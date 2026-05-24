import kotliquery.Session
import kotliquery.queryOf
import kotliquery.sessionOf
import java.time.LocalDate
import java.time.LocalTime

object CheckInService {

    enum class Status(val value: String) {
        DONE("done"),
        SKIP("skip")
    }

    fun record(reminderId: Long, userId: Long, date: LocalDate, status: Status): Boolean {
        return sessionOf(DatabaseService.dataSource).use { session ->
            session.update(
                queryOf(
                    """
                    INSERT INTO checkins (habit_id, reminder_id, check_date, status)
                    SELECT h.id, r.id, ?, ?
                    FROM habit_reminders r
                    JOIN habits h ON h.id = r.habit_id
                    WHERE r.id = ? AND h.user_id = ? AND h.status <> 'deleted'
                    ON CONFLICT (reminder_id, check_date) WHERE reminder_id IS NOT NULL DO UPDATE
                        SET status = EXCLUDED.status,
                            checked_at = now()
                    """.trimIndent(),
                    date,
                    status.value,
                    reminderId,
                    userId
                )
            ) > 0
        }
    }

    fun checkInCounter(habitId: Long, userId: Long, date: LocalDate): Boolean {
        return sessionOf(DatabaseService.dataSource).use { session ->
            session.update(
                queryOf(
                    """
                    INSERT INTO checkins (habit_id, reminder_id, check_date, status)
                    SELECT h.id, NULL, ?, 'done'
                    FROM habits h
                    WHERE h.id = ? AND h.user_id = ? AND h.status <> 'deleted'
                      AND h.habit_type = 'counter'
                    """.trimIndent(),
                    date,
                    habitId,
                    userId
                )
            ) > 0
        }
    }

    fun recordQuantity(habitId: Long, userId: Long, date: LocalDate, value: Double): Boolean {
        return sessionOf(DatabaseService.dataSource).use { session ->
            session.update(
                queryOf(
                    """
                    INSERT INTO checkins (habit_id, reminder_id, check_date, status, quantity)
                    SELECT h.id, NULL, ?, 'done', ?
                    FROM habits h
                    WHERE h.id = ? AND h.user_id = ? AND h.status <> 'deleted'
                      AND h.habit_type = 'quantity'
                    """.trimIndent(),
                    date,
                    value,
                    habitId,
                    userId
                )
            ) > 0
        }
    }

    fun todayCount(habitId: Long, date: LocalDate): Int {
        return sessionOf(DatabaseService.dataSource).use { session ->
            session.run(
                queryOf(
                    """
                    SELECT COUNT(*) AS cnt
                    FROM checkins
                    WHERE habit_id = ? AND reminder_id IS NULL AND check_date = ?
                    """.trimIndent(),
                    habitId,
                    date
                ).map { it.int("cnt") }.asSingle
            ) ?: 0
        }
    }

    fun todayQuantity(habitId: Long, date: LocalDate): Double {
        return sessionOf(DatabaseService.dataSource).use { session ->
            session.run(
                queryOf(
                    """
                    SELECT COALESCE(SUM(quantity), 0) AS total
                    FROM checkins
                    WHERE habit_id = ? AND reminder_id IS NULL AND check_date = ?
                    """.trimIndent(),
                    habitId,
                    date
                ).map { it.double("total") }.asSingle
            ) ?: 0.0
        }
    }

    private data class CounterTotals(val today: Int, val total: Int, val days: Int)

    private fun counterTotals(habitId: Long, today: LocalDate): CounterTotals {
        return sessionOf(DatabaseService.dataSource).use { session ->
            session.run(
                queryOf(
                    """
                    SELECT
                        COUNT(*) FILTER (WHERE check_date = ?) AS today_count,
                        COUNT(*)                               AS grand_total,
                        COUNT(DISTINCT check_date)             AS days_logged
                    FROM checkins
                    WHERE habit_id = ? AND reminder_id IS NULL
                    """.trimIndent(),
                    today,
                    habitId
                ).map { row ->
                    CounterTotals(
                        today = row.int("today_count"),
                        total = row.int("grand_total"),
                        days = row.int("days_logged")
                    )
                }.asSingle
            ) ?: CounterTotals(0, 0, 0)
        }
    }

    private data class QuantityTotals(val today: Double, val total: Double, val days: Int)

    private fun quantityTotals(habitId: Long, today: LocalDate): QuantityTotals {
        return sessionOf(DatabaseService.dataSource).use { session ->
            session.run(
                queryOf(
                    """
                    SELECT
                        COALESCE(SUM(quantity) FILTER (WHERE check_date = ?), 0)::float AS today_total,
                        COALESCE(SUM(quantity), 0)::float                                AS grand_total,
                        COUNT(DISTINCT check_date)                                       AS days_logged
                    FROM checkins
                    WHERE habit_id = ? AND reminder_id IS NULL AND quantity IS NOT NULL
                    """.trimIndent(),
                    today,
                    habitId
                ).map { row ->
                    QuantityTotals(
                        today = row.double("today_total"),
                        total = row.double("grand_total"),
                        days = row.int("days_logged")
                    )
                }.asSingle
            ) ?: QuantityTotals(0.0, 0.0, 0)
        }
    }

    sealed class HabitStat {
        abstract val habitId: Long
        abstract val name: String

        data class Scheduled(
            override val habitId: Long,
            override val name: String,
            val totalDays: Int,
            val doneCount: Int,
            val skipCount: Int,
            val streak: Int
        ) : HabitStat()

        sealed class Counter : HabitStat() {
            abstract val todayCount: Int

            data class WithTarget(
                override val habitId: Long,
                override val name: String,
                val dailyTarget: Int,
                val direction: HabitService.Direction?,
                override val todayCount: Int,
                val doneDays: Int,
                val skipDays: Int,
                val streak: Int
            ) : Counter()

            data class Trend(
                override val habitId: Long,
                override val name: String,
                val direction: HabitService.Direction,
                override val todayCount: Int,
                val yesterdayCount: Int,
                val grandTotal: Int,
                val daysLogged: Int,
                val overallAvg: Double,
                val recent3Avg: Double,
                val previous3Avg: Double,
                val recent7Avg: Double,
                val previous7Avg: Double
            ) : Counter()

            data class Plain(
                override val habitId: Long,
                override val name: String,
                override val todayCount: Int,
                val grandTotal: Int,
                val daysLogged: Int
            ) : Counter()
        }

        sealed class Quantity : HabitStat() {
            abstract val todayTotal: Double
            abstract val unit: String?

            data class WithTarget(
                override val habitId: Long,
                override val name: String,
                override val unit: String?,
                val dailyTarget: Double,
                val direction: HabitService.Direction?,
                override val todayTotal: Double,
                val doneDays: Int,
                val skipDays: Int,
                val streak: Int
            ) : Quantity()

            data class Trend(
                override val habitId: Long,
                override val name: String,
                override val unit: String?,
                val direction: HabitService.Direction,
                override val todayTotal: Double,
                val yesterdayTotal: Double,
                val grandTotal: Double,
                val daysLogged: Int,
                val overallAvg: Double,
                val recent3Avg: Double,
                val previous3Avg: Double,
                val recent7Avg: Double,
                val previous7Avg: Double
            ) : Quantity()

            data class Plain(
                override val habitId: Long,
                override val name: String,
                override val unit: String?,
                override val todayTotal: Double,
                val grandTotal: Double,
                val daysLogged: Int
            ) : Quantity()
        }
    }

    fun userStats(userId: Long, today: LocalDate): List<HabitStat> {
        val habits = HabitService.listActive(userId)
        return sessionOf(DatabaseService.dataSource).use { session ->
            habits.map { h ->
                when (h.type) {
                    HabitService.Type.SCHEDULED -> scheduledStat(session, h)
                    HabitService.Type.COUNTER -> counterStat(session, h, today)
                    HabitService.Type.QUANTITY -> quantityStat(session, h, today)
                }
            }
        }
    }

    private fun quantityStat(session: Session, h: HabitService.Habit, today: LocalDate): HabitStat.Quantity {
        val target = h.targetValue
        val direction = h.direction
        return when {
            target != null -> quantityWithTarget(session, h, today, target)
            direction != null -> quantityTrend(session, h, today, direction)
            else -> {
                val t = quantityTotals(h.id, today)
                HabitStat.Quantity.Plain(h.id, h.name, h.unit, t.today, t.total, t.days)
            }
        }
    }

    private fun quantityWithTarget(
        session: Session,
        h: HabitService.Habit,
        today: LocalDate,
        target: Double
    ): HabitStat.Quantity.WithTarget {
        val hitExpr = if (h.direction == HabitService.Direction.LESS) "amt <= ?" else "amt >= ?"
        val yesterday = today.minusDays(1)
        val row = session.run(
            queryOf(
                """
                WITH bounds AS (
                    SELECT (h.created_at AT TIME ZONE us.timezone)::date AS start_d
                    FROM habits h
                    JOIN user_settings us ON us.user_id = h.user_id
                    WHERE h.id = ?
                ),
                days AS (
                    SELECT generate_series(b.start_d, ?::date, '1 day')::date AS d
                    FROM bounds b
                ),
                amounts AS (
                    SELECT days.d, COALESCE(c.amt, 0)::float AS amt
                    FROM days
                    LEFT JOIN (
                        SELECT check_date, SUM(quantity) AS amt
                        FROM checkins
                        WHERE habit_id = ? AND reminder_id IS NULL AND quantity IS NOT NULL
                        GROUP BY check_date
                    ) c ON c.check_date = days.d
                ),
                met AS (
                    SELECT d, amt, $hitExpr AS hit FROM amounts
                ),
                streak_groups AS (
                    SELECT d, d + (ROW_NUMBER() OVER (ORDER BY d DESC))::int AS grp
                    FROM met
                    WHERE hit = TRUE
                ),
                streak AS (
                    SELECT COUNT(*) AS s
                    FROM streak_groups
                    WHERE grp = (
                        SELECT grp FROM streak_groups
                        WHERE d >= ?::date
                        ORDER BY d DESC LIMIT 1
                    )
                )
                SELECT
                    (SELECT amt FROM amounts WHERE d = ?::date)             AS today_total,
                    (SELECT COUNT(*) FROM met WHERE d < ?::date AND hit)    AS done_days,
                    (SELECT COUNT(*) FROM met WHERE d < ?::date AND NOT hit) AS skip_days,
                    (SELECT COALESCE(s, 0) FROM streak)                     AS streak
                """.trimIndent(),
                h.id,
                today,
                h.id,
                target,
                yesterday,
                today,
                today,
                today
            ).map { r ->
                QuantityStreakRow(
                    todayTotal = r.doubleOrNull("today_total") ?: 0.0,
                    doneDays = r.int("done_days"),
                    skipDays = r.int("skip_days"),
                    streak = r.int("streak")
                )
            }.asSingle
        ) ?: QuantityStreakRow(0.0, 0, 0, 0)

        return HabitStat.Quantity.WithTarget(
            habitId = h.id,
            name = h.name,
            unit = h.unit,
            dailyTarget = target,
            direction = h.direction,
            todayTotal = row.todayTotal,
            doneDays = row.doneDays,
            skipDays = row.skipDays,
            streak = row.streak
        )
    }

    private fun quantityTrend(
        session: Session,
        h: HabitService.Habit,
        today: LocalDate,
        direction: HabitService.Direction
    ): HabitStat.Quantity.Trend {
        val totals = quantityTotals(h.id, today)
        val row = session.run(
            queryOf(
                """
                WITH p AS (SELECT ?::date AS today),
                days AS (
                    SELECT generate_series(p.today - 13, p.today, '1 day')::date AS d FROM p
                ),
                amounts AS (
                    SELECT days.d, COALESCE(c.amt, 0)::float AS amt
                    FROM days
                    LEFT JOIN (
                        SELECT check_date, SUM(quantity) AS amt
                        FROM checkins
                        WHERE habit_id = ? AND reminder_id IS NULL AND quantity IS NOT NULL
                        GROUP BY check_date
                    ) c ON c.check_date = days.d
                )
                SELECT
                    COALESCE((SELECT amt FROM amounts c, p WHERE c.d = p.today), 0)                                                    AS today_total,
                    COALESCE((SELECT amt FROM amounts c, p WHERE c.d = p.today - 1), 0)                                                AS yesterday_total,
                    COALESCE((SELECT AVG(amt) FROM amounts c, p WHERE c.d >= p.today - 2), 0)                                          AS recent3,
                    COALESCE((SELECT AVG(amt) FROM amounts c, p WHERE c.d >= p.today - 5 AND c.d <= p.today - 3), 0)                   AS previous3,
                    COALESCE((SELECT AVG(amt) FROM amounts c, p WHERE c.d >= p.today - 6), 0)                                          AS recent7,
                    COALESCE((SELECT AVG(amt) FROM amounts c, p WHERE c.d >= p.today - 13 AND c.d <= p.today - 7), 0)                  AS previous7
                """.trimIndent(),
                today,
                h.id
            ).map { r ->
                TrendRow(
                    today = r.double("today_total"),
                    yesterday = r.double("yesterday_total"),
                    recent3 = r.double("recent3"),
                    previous3 = r.double("previous3"),
                    recent7 = r.double("recent7"),
                    previous7 = r.double("previous7")
                )
            }.asSingle
        ) ?: TrendRow(0.0, 0.0, 0.0, 0.0, 0.0, 0.0)

        val overallAvg = if (totals.days > 0) totals.total / totals.days else 0.0
        return HabitStat.Quantity.Trend(
            habitId = h.id,
            name = h.name,
            unit = h.unit,
            direction = direction,
            todayTotal = totals.today,
            yesterdayTotal = row.yesterday,
            grandTotal = totals.total,
            daysLogged = totals.days,
            overallAvg = overallAvg,
            recent3Avg = row.recent3,
            previous3Avg = row.previous3,
            recent7Avg = row.recent7,
            previous7Avg = row.previous7
        )
    }

    private data class QuantityStreakRow(val todayTotal: Double, val doneDays: Int, val skipDays: Int, val streak: Int)

    private fun counterStat(session: Session, h: HabitService.Habit, today: LocalDate): HabitStat.Counter {
        val target = h.dailyTarget
        val direction = h.direction
        return when {
            target != null -> counterWithTarget(session, h, today, target)
            direction != null -> counterTrend(session, h, today, direction)
            else -> {
                val t = counterTotals(h.id, today)
                HabitStat.Counter.Plain(h.id, h.name, t.today, t.total, t.days)
            }
        }
    }

    private fun counterWithTarget(
        session: Session,
        h: HabitService.Habit,
        today: LocalDate,
        target: Int
    ): HabitStat.Counter.WithTarget {
        val hitExpr = if (h.direction == HabitService.Direction.LESS) "cnt <= ?" else "cnt >= ?"
        val yesterday = today.minusDays(1)
        val row = session.run(
            queryOf(
                """
                WITH bounds AS (
                    SELECT (h.created_at AT TIME ZONE us.timezone)::date AS start_d
                    FROM habits h
                    JOIN user_settings us ON us.user_id = h.user_id
                    WHERE h.id = ?
                ),
                days AS (
                    SELECT generate_series(b.start_d, ?::date, '1 day')::date AS d
                    FROM bounds b
                ),
                counts AS (
                    SELECT days.d, COALESCE(c.cnt, 0) AS cnt
                    FROM days
                    LEFT JOIN (
                        SELECT check_date, COUNT(*) AS cnt
                        FROM checkins
                        WHERE habit_id = ? AND reminder_id IS NULL
                        GROUP BY check_date
                    ) c ON c.check_date = days.d
                ),
                met AS (
                    SELECT d, cnt, $hitExpr AS hit FROM counts
                ),
                streak_groups AS (
                    SELECT d, d + (ROW_NUMBER() OVER (ORDER BY d DESC))::int AS grp
                    FROM met
                    WHERE hit = TRUE
                ),
                streak AS (
                    SELECT COUNT(*) AS s
                    FROM streak_groups
                    WHERE grp = (
                        SELECT grp FROM streak_groups
                        WHERE d >= ?::date
                        ORDER BY d DESC LIMIT 1
                    )
                )
                SELECT
                    (SELECT cnt FROM counts WHERE d = ?::date)              AS today_count,
                    (SELECT COUNT(*) FROM met WHERE d < ?::date AND hit)    AS done_days,
                    (SELECT COUNT(*) FROM met WHERE d < ?::date AND NOT hit) AS skip_days,
                    (SELECT COALESCE(s, 0) FROM streak)                    AS streak
                """.trimIndent(),
                h.id,
                today,
                h.id,
                target,
                yesterday,
                today,
                today,
                today
            ).map { r ->
                StreakRow(
                    todayCount = r.intOrNull("today_count") ?: 0,
                    doneDays = r.int("done_days"),
                    skipDays = r.int("skip_days"),
                    streak = r.int("streak")
                )
            }.asSingle
        ) ?: StreakRow(0, 0, 0, 0)

        return HabitStat.Counter.WithTarget(
            habitId = h.id,
            name = h.name,
            dailyTarget = target,
            direction = h.direction,
            todayCount = row.todayCount,
            doneDays = row.doneDays,
            skipDays = row.skipDays,
            streak = row.streak
        )
    }

    private fun counterTrend(
        session: Session,
        h: HabitService.Habit,
        today: LocalDate,
        direction: HabitService.Direction
    ): HabitStat.Counter.Trend {
        val totals = counterTotals(h.id, today)
        val row = session.run(
            queryOf(
                """
                WITH p AS (SELECT ?::date AS today),
                days AS (
                    SELECT generate_series(p.today - 13, p.today, '1 day')::date AS d FROM p
                ),
                counts AS (
                    SELECT days.d, COALESCE(c.cnt, 0)::float AS cnt
                    FROM days
                    LEFT JOIN (
                        SELECT check_date, COUNT(*) AS cnt
                        FROM checkins
                        WHERE habit_id = ? AND reminder_id IS NULL
                        GROUP BY check_date
                    ) c ON c.check_date = days.d
                )
                SELECT
                    COALESCE((SELECT cnt FROM counts c, p WHERE c.d = p.today), 0)                                                   AS today_count,
                    COALESCE((SELECT cnt FROM counts c, p WHERE c.d = p.today - 1), 0)                                               AS yesterday_count,
                    COALESCE((SELECT AVG(cnt) FROM counts c, p WHERE c.d >= p.today - 2), 0)                                          AS recent3,
                    COALESCE((SELECT AVG(cnt) FROM counts c, p WHERE c.d >= p.today - 5 AND c.d <= p.today - 3), 0)                  AS previous3,
                    COALESCE((SELECT AVG(cnt) FROM counts c, p WHERE c.d >= p.today - 6), 0)                                          AS recent7,
                    COALESCE((SELECT AVG(cnt) FROM counts c, p WHERE c.d >= p.today - 13 AND c.d <= p.today - 7), 0)                  AS previous7
                """.trimIndent(),
                today,
                h.id
            ).map { r ->
                TrendRow(
                    today = r.double("today_count"),
                    yesterday = r.double("yesterday_count"),
                    recent3 = r.double("recent3"),
                    previous3 = r.double("previous3"),
                    recent7 = r.double("recent7"),
                    previous7 = r.double("previous7")
                )
            }.asSingle
        ) ?: TrendRow(0.0, 0.0, 0.0, 0.0, 0.0, 0.0)

        val overallAvg = if (totals.days > 0) totals.total.toDouble() / totals.days else 0.0
        return HabitStat.Counter.Trend(
            habitId = h.id,
            name = h.name,
            direction = direction,
            todayCount = totals.today,
            yesterdayCount = row.yesterday.toInt(),
            grandTotal = totals.total,
            daysLogged = totals.days,
            overallAvg = overallAvg,
            recent3Avg = row.recent3,
            previous3Avg = row.previous3,
            recent7Avg = row.recent7,
            previous7Avg = row.previous7
        )
    }

    private data class TrendRow(
        val today: Double,
        val yesterday: Double,
        val recent3: Double,
        val previous3: Double,
        val recent7: Double,
        val previous7: Double
    )

    private data class StreakRow(val todayCount: Int, val doneDays: Int, val skipDays: Int, val streak: Int)

    private fun scheduledStat(session: Session, habit: HabitService.Habit): HabitStat.Scheduled {
        val row = session.run(
            queryOf(
                """
                SELECT COUNT(DISTINCT c.check_date)                       AS total_days,
                       COUNT(*) FILTER (WHERE c.status = 'done')          AS done_count,
                       COUNT(*) FILTER (WHERE c.status = 'skip')          AS skip_count
                FROM checkins c
                WHERE c.habit_id = ? AND c.reminder_id IS NOT NULL
                """.trimIndent(),
                habit.id
            ).map { r ->
                Triple(r.int("total_days"), r.int("done_count"), r.int("skip_count"))
            }.asSingle
        ) ?: Triple(0, 0, 0)

        return HabitStat.Scheduled(
            habitId = habit.id,
            name = habit.name,
            totalDays = row.first,
            doneCount = row.second,
            skipCount = row.third,
            streak = currentStreak(session, habit.id)
        )
    }

    private fun currentStreak(session: Session, habitId: Long): Int {
        return session.run(
            queryOf(
                """
                WITH daily AS (
                    SELECT c.check_date,
                           BOOL_OR(c.status = 'done') AS any_done
                    FROM checkins c
                    WHERE c.habit_id = ? AND c.reminder_id IS NOT NULL
                    GROUP BY c.check_date
                ),
                with_gap AS (
                    SELECT check_date,
                           any_done,
                           check_date + (ROW_NUMBER() OVER (ORDER BY check_date DESC))::int AS grp
                    FROM daily
                    WHERE any_done = TRUE
                )
                SELECT COUNT(*) AS streak
                FROM with_gap
                WHERE grp = (
                    SELECT grp FROM with_gap ORDER BY check_date DESC LIMIT 1
                )
                """.trimIndent(),
                habitId
            ).map { it.int("streak") }.asSingle
        ) ?: 0
    }

    data class PendingCheckIn(
        val reminderId: Long,
        val name: String,
        val reminderTime: LocalTime,
        val date: LocalDate
    )

    fun pendingCheckIns(userId: Long, fromDate: LocalDate, toDate: LocalDate): List<PendingCheckIn> {
        return sessionOf(DatabaseService.dataSource).use { session ->
            session.run(
                queryOf(
                    """
                    WITH date_range AS (
                        SELECT generate_series(?::date, ?::date, '1 day')::date AS d
                    )
                    SELECT r.id AS reminder_id, h.name, r.reminder_time, dr.d AS check_date
                    FROM habits h
                    JOIN habit_reminders r ON r.habit_id = h.id
                    JOIN user_settings us ON us.user_id = h.user_id
                    CROSS JOIN date_range dr
                    LEFT JOIN checkins c
                        ON c.reminder_id = r.id
                       AND c.check_date = dr.d
                    WHERE h.user_id = ?
                      AND h.status = 'active'
                      AND h.habit_type = 'scheduled'
                      AND c.id IS NULL
                      AND ((dr.d + r.reminder_time) AT TIME ZONE us.timezone) > h.created_at
                      AND ((dr.d + r.reminder_time) AT TIME ZONE us.timezone) <= now()
                    ORDER BY dr.d, r.reminder_time, h.created_at
                    """.trimIndent(),
                    fromDate,
                    toDate,
                    userId
                ).map { row ->
                    PendingCheckIn(
                        reminderId = row.long("reminder_id"),
                        name = row.string("name"),
                        reminderTime = row.localTime("reminder_time"),
                        date = row.localDate("check_date")
                    )
                }.asList
            )
        }
    }

    fun autoSkipOverdue() {
        sessionOf(DatabaseService.dataSource).use { session ->
            session.execute(
                queryOf(
                    """
                    WITH slots AS (
                        SELECT h.id AS habit_id, r.id AS reminder_id,
                               h.created_at, h.paused_at, h.status,
                               r.reminder_time, us.timezone,
                               generate_series(
                                   (h.created_at AT TIME ZONE us.timezone)::date,
                                   (now() AT TIME ZONE us.timezone)::date,
                                   '1 day'
                               )::date AS d
                        FROM habit_reminders r
                        JOIN habits h ON h.id = r.habit_id
                        JOIN user_settings us ON us.user_id = h.user_id
                        WHERE h.status <> 'deleted'
                          AND h.habit_type = 'scheduled'
                    )
                    INSERT INTO checkins (habit_id, reminder_id, check_date, status)
                    SELECT s.habit_id, s.reminder_id, s.d, 'skip'
                    FROM slots s
                    WHERE ((s.d + s.reminder_time) AT TIME ZONE s.timezone) > s.created_at
                      AND s.d < ((now() AT TIME ZONE s.timezone)::date - 1)
                      AND (s.status = 'active' OR ((s.d + s.reminder_time) AT TIME ZONE s.timezone) < s.paused_at)
                    ON CONFLICT (reminder_id, check_date) WHERE reminder_id IS NOT NULL DO NOTHING
                    """.trimIndent()
                )
            )
        }
    }
}
