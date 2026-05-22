import kotliquery.queryOf
import kotliquery.sessionOf
import java.time.LocalDate
import java.time.LocalTime

object CheckInService {

    enum class Status(val value: String) {
        DONE("done"),
        SKIP("skip")
    }

    fun record(
        habitId: Long,
        userId: Long,
        reminderTime: LocalTime,
        date: LocalDate,
        status: Status
    ): Boolean {
        return sessionOf(DatabaseService.dataSource).use { session ->
            session.update(
                queryOf(
                    """
                    INSERT INTO checkins (habit_id, user_id, reminder_time, check_date, status)
                    VALUES (?, ?, ?, ?, ?)
                    ON CONFLICT (habit_id, check_date, reminder_time) DO UPDATE
                        SET status = EXCLUDED.status,
                            checked_at = now()
                    """.trimIndent(),
                    habitId,
                    userId,
                    reminderTime,
                    date,
                    status.value
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
        return sessionOf(DatabaseService.dataSource).run(
            queryOf(
                """
                SELECT h.id, h.name,
                       COUNT(DISTINCT c.check_date)                                       AS total_days,
                       COUNT(*) FILTER (WHERE c.status = 'done')                          AS done_count,
                       COUNT(*) FILTER (WHERE c.status = 'skip')                          AS skip_count
                FROM habits h
                LEFT JOIN checkins c ON c.habit_id = h.id
                WHERE h.user_id = ? AND h.deleted_at IS NULL
                GROUP BY h.id, h.name
                ORDER BY h.created_at
                """.trimIndent(),
                userId
            ).map { row ->
                val habitId = row.long("id")
                HabitStat(
                    habitId = habitId,
                    name = row.string("name"),
                    totalDays = row.int("total_days"),
                    doneCount = row.int("done_count"),
                    skipCount = row.int("skip_count"),
                    streak = currentStreak(habitId)
                )
            }.asList
        )
    }

    private fun currentStreak(habitId: Long): Int {
        return sessionOf(DatabaseService.dataSource).run(
            queryOf(
                """
                WITH daily AS (
                    SELECT check_date,
                           BOOL_OR(status = 'done') AS any_done
                    FROM checkins
                    WHERE habit_id = ?
                    GROUP BY check_date
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

    fun todaysCheckIns(userId: Long, date: LocalDate): List<TodayCheckIn> {
        return sessionOf(DatabaseService.dataSource).run(
            queryOf(
                """
                SELECT h.id        AS habit_id,
                       h.name      AS name,
                       r.reminder_time,
                       c.status
                FROM habits h
                JOIN habit_reminders r ON r.habit_id = h.id
                LEFT JOIN checkins c
                    ON c.habit_id = h.id
                   AND c.reminder_time = r.reminder_time
                   AND c.check_date = ?
                WHERE h.user_id = ? AND h.deleted_at IS NULL
                ORDER BY r.reminder_time, h.created_at
                """.trimIndent(),
                date,
                userId
            ).map { row ->
                TodayCheckIn(
                    habitId = row.long("habit_id"),
                    name = row.string("name"),
                    reminderTime = row.localTime("reminder_time"),
                    status = row.stringOrNull("status")
                )
            }.asList
        )
    }

    data class TodayCheckIn(
        val habitId: Long,
        val name: String,
        val reminderTime: LocalTime,
        val status: String?
    )
}
