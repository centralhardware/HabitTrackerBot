package db

import DatabaseService
import dto.Habit
import dto.RawDue
import dto.toHabit
import dto.toRawDue
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using

object HabitRepository {

    fun find(habitId: Long, userId: Long): Habit? =
        listActive(userId).firstOrNull { it.id == habitId }

    fun listActive(userId: Long): List<Habit> {
        return sessionOf(DatabaseService.dataSource).use { session ->
            session.run(
                queryOf(
                    """
                    SELECT h.id, h.user_id, h.name, h.habit_type, h.daily_target,
                           h.unit, h.direction, h.status,
                           COALESCE(
                               ARRAY_AGG(r.reminder_time ORDER BY r.reminder_time)
                                   FILTER (WHERE r.reminder_time IS NOT NULL),
                               '{}'
                           ) AS times
                    FROM habits h
                    LEFT JOIN habit_reminders r ON r.habit_id = h.id
                    WHERE h.user_id = ? AND h.status <> 'deleted'
                    GROUP BY h.id
                    ORDER BY h.created_at
                    """.trimIndent(),
                    userId
                ).map { it.toHabit() }.asList
            )
        }
    }

    fun upsert(habit: Habit): Habit =
        if (habit.id == 0L) insert(habit) else update(habit)

    private fun insert(habit: Habit): Habit {
        return using(sessionOf(DatabaseService.dataSource, returnGeneratedKey = true)) { session ->
            session.transaction { tx ->
                val id = tx.updateAndReturnGeneratedKey(
                    queryOf(
                        """
                        INSERT INTO habits (user_id, name, habit_type, daily_target, unit, direction, status)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """.trimIndent(),
                        habit.userId, habit.name, habit.type.value,
                        habit.dailyTarget, habit.unit, habit.direction?.value,
                        habit.status.value
                    )
                ) ?: error("Failed to insert habit")

                habit.reminders.forEach { time ->
                    tx.execute(
                        queryOf(
                            "INSERT INTO habit_reminders (habit_id, reminder_time) VALUES (?, ?)",
                            id, time
                        )
                    )
                }
                habit.copy(id = id)
            }
        }
    }

    private fun update(habit: Habit): Habit {
        sessionOf(DatabaseService.dataSource).use { session ->
            session.update(
                queryOf(
                    """
                    UPDATE habits
                    SET name         = ?,
                        habit_type   = ?,
                        daily_target = ?,
                        unit         = ?,
                        direction    = ?,
                        status       = ?,
                        paused_at    = CASE WHEN ? = 'paused'  AND status <> 'paused'  THEN now() ELSE paused_at  END,
                        deleted_at   = CASE WHEN ? = 'deleted' AND status <> 'deleted' THEN now() ELSE deleted_at END
                    WHERE id = ? AND user_id = ?
                    """.trimIndent(),
                    habit.name, habit.type.value, habit.dailyTarget, habit.unit, habit.direction?.value,
                    habit.status.value,
                    habit.status.value, habit.status.value,
                    habit.id, habit.userId
                )
            )
        }
        return habit
    }

    fun findHabitIdByReminder(reminderId: Long, userId: Long): Long? {
        return sessionOf(DatabaseService.dataSource).use { session ->
            session.run(
                queryOf(
                    """
                    SELECT h.id
                    FROM habit_reminders r
                    JOIN habits h ON h.id = r.habit_id
                    WHERE r.id = ? AND h.user_id = ? AND h.status <> 'deleted'
                    """.trimIndent(),
                    reminderId, userId
                ).map { it.long("id") }.asSingle
            )
        }
    }

    fun findRawDue(): List<RawDue> {
        return sessionOf(DatabaseService.dataSource).use { session ->
            session.run(
                queryOf(
                    """
                    SELECT r.id AS reminder_id, h.id AS habit_id, h.habit_type,
                           h.user_id, h.name, r.reminder_time,
                           us.timezone AS tz, us.language AS lang
                    FROM habit_reminders r
                    JOIN habits h ON h.id = r.habit_id
                    JOIN user_settings us ON us.user_id = h.user_id
                    LEFT JOIN checkins c
                        ON c.reminder_id = r.id
                       AND c.check_date = (now() AT TIME ZONE us.timezone)::date
                    WHERE h.status = 'active'
                      AND us.timezone IS NOT NULL
                      AND (h.habit_type <> 'scheduled' OR c.id IS NULL)
                    """.trimIndent()
                ).map { it.toRawDue() }.asList
            )
        }
    }
}
