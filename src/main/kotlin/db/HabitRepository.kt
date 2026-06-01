package db

import services.DatabaseService
import dto.Habit
import dto.HabitParam
import dto.HabitType
import dto.RawDue
import dto.RawMissed
import dto.toHabit
import dto.toHabitParam
import dto.toHabitReminder
import dto.toRawDue
import dto.toRawMissed
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using

object HabitRepository {

    fun find(habitId: Long, userId: Long): Habit? =
        listRawActive(userId).firstOrNull { it.id == habitId }

    fun listActive(userId: Long): List<Habit> = listRawActive(userId)

    private fun listRawActive(userId: Long): List<Habit> {
        return sessionOf(DatabaseService.dataSource).use { session ->
            val habits = session.run(
                queryOf(
                    """
                    SELECT h.id, h.user_id, h.name, h.habit_type, h.daily_target,
                           h.unit, h.direction, h.status, h.log_only
                    FROM habits h
                    WHERE h.user_id = ? AND h.status <> 'deleted'
                    ORDER BY h.created_at
                    """.trimIndent(),
                    userId
                ).map { it.toHabit() }.asList
            )
            if (habits.isEmpty()) return@use habits

            val remindersByHabit = session.run(
                queryOf(
                    """
                    SELECT r.id, r.habit_id, r.reminder_time, r.reminder_days, r.next_day
                    FROM habit_reminders r
                    JOIN habits h ON h.id = r.habit_id
                    WHERE h.user_id = ? AND h.status <> 'deleted'
                    ORDER BY r.reminder_time
                    """.trimIndent(),
                    userId
                ).map { it.long("habit_id") to it.toHabitReminder() }.asList
            ).groupBy({ it.first }, { it.second })

            val paramsByHabit = session.run(
                queryOf(
                    """
                    SELECT p.id, p.habit_id, p.name, p.unit, p.direction, p.daily_target, p.position, p.param_type
                    FROM habit_params p
                    JOIN habits h ON h.id = p.habit_id
                    WHERE h.user_id = ? AND h.status <> 'deleted'
                    ORDER BY p.habit_id, p.position, p.id
                    """.trimIndent(),
                    userId
                ).map { it.long("habit_id") to it.toHabitParam() }.asList
            ).groupBy({ it.first }, { it.second })

            habits.map { h ->
                val params = paramsByHabit[h.id].orEmpty()
                // Single-field quantity habits keep their metadata on the param row; hoist it onto
                // the habit so every single-field habit looks the same to callers.
                val hoisted = if (h.type == HabitType.QUANTITY && params.size == 1) {
                    val p = params[0]
                    h.copy(
                        unit = h.unit ?: p.unit,
                        dailyTarget = h.dailyTarget ?: p.dailyTarget,
                        direction = h.direction ?: p.direction,
                    )
                } else h
                hoisted.copy(
                    reminders = remindersByHabit[h.id].orEmpty(),
                    params = params,
                )
            }
        }
    }

    fun upsert(habit: Habit): Habit =
        if (habit.id == 0L) insert(habit) else update(habit)

    /** Inserts a habit, its reminders and its params (every habit carries >=1 param). */
    private fun insert(habit: Habit): Habit {
        return using(sessionOf(DatabaseService.dataSource, returnGeneratedKey = true)) { session ->
            session.transaction { tx ->
                val id = tx.updateAndReturnGeneratedKey(
                    queryOf(
                        """
                        INSERT INTO habits (user_id, name, habit_type, daily_target, unit, direction, status, log_only)
                        VALUES (?, ?, ?::habit_type, ?, ?, ?::habit_direction, ?::habit_status, ?)
                        """.trimIndent(),
                        habit.userId, habit.name, habit.type.value,
                        habit.dailyTarget, habit.unit, habit.direction?.value,
                        habit.status.value, habit.logOnly
                    )
                ) ?: error("Failed to insert habit")

                habit.reminders.forEach { rem ->
                    tx.execute(
                        queryOf(
                            "INSERT INTO habit_reminders (habit_id, reminder_time, reminder_days, next_day) VALUES (?, ?, ?::int[], ?)",
                            id, rem.time, rem.days.toPgArray(), rem.nextDay
                        )
                    )
                }

                val params = habit.params.ifEmpty {
                    val defaultType = if (habit.type == HabitType.QUANTITY) dto.ParamType.NUMBER else null
                    listOf(HabitParam(id = 0, paramType = defaultType))
                }
                val savedParams = params.mapIndexed { i, p ->
                    val pid = tx.updateAndReturnGeneratedKey(
                        queryOf(
                            """
                            INSERT INTO habit_params (habit_id, name, unit, direction, daily_target, position, param_type)
                            VALUES (?, ?, ?, ?::habit_direction, ?, ?, ?::param_type)
                            """.trimIndent(),
                            id, p.name, p.unit, p.direction?.value, p.dailyTarget, i, p.paramType?.value
                        )
                    ) ?: error("Failed to insert habit param")
                    p.copy(id = pid, habitId = id, position = i)
                }

                habit.copy(id = id, params = savedParams)
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
                        log_only     = ?,
                        paused_at    = CASE WHEN ?::habit_status = 'paused'  AND status <> 'paused'  THEN now() ELSE paused_at  END,
                        deleted_at   = CASE WHEN ?::habit_status = 'deleted' AND status <> 'deleted' THEN now() ELSE deleted_at END
                    WHERE id = ? AND user_id = ?
                    """.trimIndent(),
                    habit.name, habit.type.value, habit.dailyTarget, habit.unit, habit.direction?.value,
                    habit.status.value, habit.logOnly,
                    habit.status.value, habit.status.value,
                    habit.id, habit.userId
                )
            )
        }
        return habit
    }

    /** The first (lowest-position) param id of a habit — used to write status/quantity rows. */
    fun firstParamId(habitId: Long, userId: Long): Long? {
        return sessionOf(DatabaseService.dataSource).use { session ->
            session.run(
                queryOf(
                    """
                    SELECT p.id
                    FROM habit_params p
                    JOIN habits h ON h.id = p.habit_id
                    WHERE p.habit_id = ? AND h.user_id = ? AND h.status <> 'deleted'
                    ORDER BY p.position, p.id
                    LIMIT 1
                    """.trimIndent(),
                    habitId, userId
                ).map { it.long("id") }.asSingle
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
                           h.user_id, h.name, r.reminder_time, r.reminder_days, r.next_day,
                           us.timezone AS tz, us.language AS lang
                    FROM habit_reminders r
                    JOIN habits h ON h.id = r.habit_id
                    JOIN user_settings us ON us.user_id = h.user_id
                    LEFT JOIN checkins c
                        ON c.reminder_id = r.id
                       AND c.check_date = CASE WHEN r.next_day
                           THEN (now() AT TIME ZONE us.timezone)::date - 1
                           ELSE (now() AT TIME ZONE us.timezone)::date
                       END
                    WHERE h.status = 'active'
                      AND us.timezone IS NOT NULL
                      AND (h.habit_type <> 'scheduled' OR c.id IS NULL)
                    """.trimIndent()
                ).map { it.toRawDue() }.asList
            )
        }
    }

    /**
     * Backfills checkin rows for every scheduled reminder slot that has fired
     * but has no checkin yet (going back to habit creation). Slots whose firing
     * moment is older than 24h are inserted as `skip` immediately; recent slots
     * are inserted as `pending` (status NULL) and returned so the caller can
     * send a catch-up notification.
     *
     * Recent (pending) rows keep `checked_at` NULL — there's no check-in yet; the
     * old rows backfilled straight to `skip` get `checked_at = now()` as their skip
     * moment. The skip/pending split is decided off the slot's firing moment.
     */
    fun backfillMissedScheduled(): List<RawMissed> {
        return sessionOf(DatabaseService.dataSource).use { session ->
            session.run(
                queryOf(
                    """
                    WITH missed AS (
                        SELECT r.id AS reminder_id, r.habit_id, h.user_id,
                               d::date AS missed_date,
                               CASE WHEN r.next_day
                                   THEN (((d::date + 1) + r.reminder_time) AT TIME ZONE us.timezone)
                                   ELSE ((d::date + r.reminder_time) AT TIME ZONE us.timezone)
                               END AS fired_at,
                               us.language AS lang_code,
                               h.name AS habit_name,
                               r.reminder_time
                        FROM habit_reminders r
                        JOIN habits h ON h.id = r.habit_id
                        JOIN user_settings us ON us.user_id = h.user_id
                        CROSS JOIN LATERAL generate_series(
                            (h.created_at AT TIME ZONE us.timezone)::date,
                            (now()       AT TIME ZONE us.timezone)::date,
                            INTERVAL '1 day'
                        ) AS d
                        LEFT JOIN checkins c
                            ON c.reminder_id = r.id
                           AND c.check_date  = d::date
                        WHERE h.status = 'active'
                          AND h.habit_type = 'scheduled'
                          AND us.timezone IS NOT NULL
                          AND c.id IS NULL
                          AND (r.reminder_days IS NULL
                               OR EXTRACT(ISODOW FROM d::date)::int = ANY(r.reminder_days))
                          AND ((d::date + r.reminder_time)
                                  AT TIME ZONE us.timezone)
                              < now() - INTERVAL '1 minute'
                    ),
                    ins_events AS (
                        INSERT INTO checkins (user_id, check_date, reminder_id, habit_id, comment, checked_at)
                        SELECT user_id, missed_date, reminder_id, habit_id, NULL,
                               CASE WHEN now() - fired_at > INTERVAL '24 hours'
                                    THEN now()
                                    ELSE NULL END
                        FROM missed
                        ON CONFLICT (reminder_id, check_date)
                            WHERE reminder_id IS NOT NULL DO NOTHING
                        RETURNING id, reminder_id, check_date
                    ),
                    ins_values AS (
                        INSERT INTO checkin_values (checkin_id, param_id, status, value)
                        SELECT ie.id,
                               (SELECT hp.id FROM habit_params hp
                                WHERE hp.habit_id = m.habit_id
                                ORDER BY hp.position, hp.id LIMIT 1),
                               CASE WHEN now() - m.fired_at > INTERVAL '24 hours'
                                    THEN 'skip'::checkin_status
                                    ELSE NULL END,
                               NULL
                        FROM ins_events ie
                        JOIN missed m
                          ON m.reminder_id  = ie.reminder_id
                         AND m.missed_date  = ie.check_date
                        RETURNING checkin_id
                    )
                    SELECT m.reminder_id, m.habit_id, m.user_id, m.habit_name AS name,
                           m.reminder_time, m.lang_code AS lang, m.missed_date
                    FROM ins_events ie
                    JOIN missed m
                      ON m.reminder_id = ie.reminder_id
                     AND m.missed_date = ie.check_date
                    WHERE now() - m.fired_at <= INTERVAL '24 hours'
                    ORDER BY m.missed_date, m.reminder_time
                    """.trimIndent()
                ).map { it.toRawMissed() }.asList
            )
        }
    }

    /** Renders weekday list as a Postgres int[] literal, or null (= every day) when empty. */
    private fun List<Int>.toPgArray(): String? =
        takeIf { it.isNotEmpty() }?.joinToString(",", "{", "}")
}
