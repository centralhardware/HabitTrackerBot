package db

import DatabaseService
import dto.Habit
import dto.HabitReminder
import dto.HabitStatus
import dto.RawDue
import dto.toHabit
import dto.toRawDue
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using

object HabitRepository {

    fun find(habitId: Long, userId: Long): Habit? {
        val raw = listRawActive(userId).firstOrNull { it.id == habitId } ?: return null
        return if (raw.isGroupRoot) raw.copy(fields = listFieldsRaw(userId, raw.id)) else raw
    }

    fun findAnyRow(habitId: Long, userId: Long): Habit? =
        listRawActive(userId).firstOrNull { it.id == habitId }

    fun listActive(userId: Long): List<Habit> {
        val all = listRawActive(userId)
        val fieldsByRoot = all.filter { it.isGroupField }.groupBy { it.groupId!! }
        return all
            .filter { !it.isGroupField }
            .map { row ->
                if (row.isGroupRoot) row.copy(fields = fieldsByRoot[row.id].orEmpty())
                else row
            }
    }

    private fun listFieldsRaw(userId: Long, rootId: Long): List<Habit> =
        listRawActive(userId).filter { it.groupId == rootId && it.id != rootId }

    private fun listRawActive(userId: Long): List<Habit> {
        return sessionOf(DatabaseService.dataSource).use { session ->
            session.run(
                queryOf(
                    """
                    SELECT h.id, h.user_id, h.name, h.habit_type, h.daily_target,
                           h.unit, h.direction, h.status, h.group_id,
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
                        VALUES (?, ?, ?::habit_type, ?, ?, ?::habit_direction, ?::habit_status)
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

    /**
     * Создаёт группу: корневую строку (с reminders) и поля (со своими target/unit/direction).
     * Корень получает group_id = id, поля — group_id = id корня.
     */
    fun insertGroup(root: Habit, fields: List<Habit>): Habit {
        return using(sessionOf(DatabaseService.dataSource, returnGeneratedKey = true)) { session ->
            session.transaction { tx ->
                val rootId = tx.updateAndReturnGeneratedKey(
                    queryOf(
                        """
                        INSERT INTO habits (user_id, name, habit_type, status)
                        VALUES (?, ?, ?::habit_type, ?::habit_status)
                        """.trimIndent(),
                        root.userId, root.name, root.type.value, root.status.value
                    )
                ) ?: error("Failed to insert group root")

                tx.execute(
                    queryOf("UPDATE habits SET group_id = ? WHERE id = ?", rootId, rootId)
                )

                root.reminders.forEach { time ->
                    tx.execute(
                        queryOf(
                            "INSERT INTO habit_reminders (habit_id, reminder_time) VALUES (?, ?)",
                            rootId, time
                        )
                    )
                }

                val savedFields = fields.map { f ->
                    val fid = tx.updateAndReturnGeneratedKey(
                        queryOf(
                            """
                            INSERT INTO habits (user_id, name, habit_type, daily_target, unit, direction, status, group_id)
                            VALUES (?, ?, ?::habit_type, ?, ?, ?::habit_direction, ?::habit_status, ?)
                            """.trimIndent(),
                            f.userId, f.name, f.type.value,
                            f.dailyTarget, f.unit, f.direction?.value,
                            f.status.value, rootId
                        )
                    ) ?: error("Failed to insert group field")
                    f.copy(id = fid, groupId = rootId)
                }

                root.copy(id = rootId, groupId = rootId, fields = savedFields)
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
                        habit_type   = ?::habit_type,
                        daily_target = ?,
                        unit         = ?,
                        direction    = ?::habit_direction,
                        status       = ?::habit_status,
                        paused_at    = CASE WHEN ?::habit_status = 'paused'  AND status <> 'paused'  THEN now() ELSE paused_at  END,
                        deleted_at   = CASE WHEN ?::habit_status = 'deleted' AND status <> 'deleted' THEN now() ELSE deleted_at END
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

    /**
     * Меняет статус: для одиночной — у одной строки, для корня группы — каскадно у корня и всех полей.
     */
    fun setStatusCascade(habit: Habit, status: HabitStatus): Int {
        val isRoot = habit.isGroupRoot
        return sessionOf(DatabaseService.dataSource).use { session ->
            session.update(
                queryOf(
                    """
                    UPDATE habits
                    SET status     = ?::habit_status,
                        paused_at  = CASE WHEN ?::habit_status = 'paused'  AND status <> 'paused'  THEN now() ELSE paused_at  END,
                        deleted_at = CASE WHEN ?::habit_status = 'deleted' AND status <> 'deleted' THEN now() ELSE deleted_at END
                    WHERE user_id = ?
                      AND (id = ? OR (? AND group_id = ?))
                    """.trimIndent(),
                    status.value,
                    status.value, status.value,
                    habit.userId,
                    habit.id, isRoot, habit.id
                )
            )
        }
    }

    fun listReminders(habitId: Long, userId: Long): List<HabitReminder> {
        return sessionOf(DatabaseService.dataSource).use { session ->
            session.run(
                queryOf(
                    """
                    SELECT r.id, r.reminder_time
                    FROM habit_reminders r
                    JOIN habits h ON h.id = r.habit_id
                    WHERE r.habit_id = ? AND h.user_id = ? AND h.status <> 'deleted'
                    ORDER BY r.reminder_time
                    """.trimIndent(),
                    habitId, userId
                ).map { HabitReminder(id = it.long("id"), time = it.localTime("reminder_time")) }.asList
            )
        }
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
