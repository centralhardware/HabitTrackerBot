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
                    INSERT INTO checkins (reminder_id, check_date, status)
                    SELECT r.id, ?, ?
                    FROM habit_reminders r
                    JOIN habits h ON h.id = r.habit_id
                    WHERE r.id = ? AND h.user_id = ? AND h.deleted_at IS NULL
                    ON CONFLICT (reminder_id, check_date) DO UPDATE
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

    data class HabitStat(
        val habitId: Long,
        val name: String,
        val totalDays: Int,
        val doneCount: Int,
        val skipCount: Int,
        val streak: Int
    )

    fun userStats(userId: Long): List<HabitStat> {
        return sessionOf(DatabaseService.dataSource).use { session ->
            val raw = session.run(
                queryOf(
                    """
                    SELECT h.id, h.name,
                           COUNT(DISTINCT c.check_date)                       AS total_days,
                           COUNT(*) FILTER (WHERE c.status = 'done')          AS done_count,
                           COUNT(*) FILTER (WHERE c.status = 'skip')          AS skip_count
                    FROM habits h
                    LEFT JOIN habit_reminders r ON r.habit_id = h.id
                    LEFT JOIN checkins c ON c.reminder_id = r.id
                    WHERE h.user_id = ? AND h.deleted_at IS NULL
                    GROUP BY h.id, h.name
                    ORDER BY h.created_at
                    """.trimIndent(),
                    userId
                ).map { row ->
                    StatRow(
                        habitId = row.long("id"),
                        name = row.string("name"),
                        totalDays = row.int("total_days"),
                        doneCount = row.int("done_count"),
                        skipCount = row.int("skip_count")
                    )
                }.asList
            )
            raw.map { r ->
                HabitStat(
                    habitId = r.habitId,
                    name = r.name,
                    totalDays = r.totalDays,
                    doneCount = r.doneCount,
                    skipCount = r.skipCount,
                    streak = currentStreak(session, r.habitId)
                )
            }
        }
    }

    private data class StatRow(
        val habitId: Long,
        val name: String,
        val totalDays: Int,
        val doneCount: Int,
        val skipCount: Int
    )

    private fun currentStreak(session: Session, habitId: Long): Int {
        return session.run(
            queryOf(
                """
                WITH daily AS (
                    SELECT c.check_date,
                           BOOL_OR(c.status = 'done') AS any_done
                    FROM checkins c
                    JOIN habit_reminders r ON r.id = c.reminder_id
                    WHERE r.habit_id = ?
                    GROUP BY c.check_date
                ),
                with_gap AS (
                    SELECT check_date,
                           any_done,
                           check_date - (ROW_NUMBER() OVER (ORDER BY check_date DESC))::int AS grp
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
                      AND h.deleted_at IS NULL
                      AND h.paused_at IS NULL
                      AND c.id IS NULL
                      AND ((dr.d + r.reminder_time) AT TIME ZONE us.timezone) > h.created_at
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
                        SELECT r.id AS reminder_id, h.created_at, h.paused_at,
                               r.reminder_time, us.timezone,
                               generate_series(
                                   (h.created_at AT TIME ZONE us.timezone)::date,
                                   (now() AT TIME ZONE us.timezone)::date,
                                   '1 day'
                               )::date AS d
                        FROM habit_reminders r
                        JOIN habits h ON h.id = r.habit_id
                        JOIN user_settings us ON us.user_id = h.user_id
                        WHERE h.deleted_at IS NULL
                    )
                    INSERT INTO checkins (reminder_id, check_date, status)
                    SELECT s.reminder_id, s.d, 'skip'
                    FROM slots s
                    WHERE ((s.d + s.reminder_time) AT TIME ZONE s.timezone) > s.created_at
                      AND s.d < ((now() AT TIME ZONE s.timezone)::date - 1)
                      AND (s.paused_at IS NULL OR ((s.d + s.reminder_time) AT TIME ZONE s.timezone) < s.paused_at)
                    ON CONFLICT (reminder_id, check_date) DO NOTHING
                    """.trimIndent()
                )
            )
        }
    }
}
