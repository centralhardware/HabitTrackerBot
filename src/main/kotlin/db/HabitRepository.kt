package db

import services.DatabaseService
import dto.Habit
import dto.HabitParam
import dto.HabitType
import dto.RawDue
import dto.RawMissed
import dto.ResumedHabit
import dto.toHabit
import dto.toHabitParam
import dto.toHabitReminder
import dto.toRawDue
import dto.toRawMissed
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import java.time.OffsetDateTime

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
                    SELECT r.id, r.habit_id, r.reminder_time, r.reminder_days
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
                val hoisted = if ((h.type == HabitType.QUANTITY || h.type == HabitType.TIMER) && params.size == 1) {
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
                            "INSERT INTO habit_reminders (habit_id, reminder_time, reminder_days) VALUES (?, ?, ?::int[])",
                            id, rem.offsetMinutes, rem.days.toPgArray()
                        )
                    )
                }

                val params = habit.params.ifEmpty {
                    when (habit.type) {
                        // Quantity habits get a numeric service param; timer habits store their
                        // elapsed minutes on the same kind of single numeric param.
                        HabitType.QUANTITY, HabitType.TIMER -> listOf(HabitParam(id = 0, paramType = dto.ParamType.NUMBER))
                        // Counter events are bare checkins rows; scheduled events keep their
                        // done/skip status on the checkins row — neither needs a param.
                        HabitType.COUNTER, HabitType.SCHEDULED -> emptyList()
                    }
                }
                val savedParams = params.mapIndexed { i, p ->
                    val pid = tx.updateAndReturnGeneratedKey(
                        queryOf(
                            """
                            INSERT INTO habit_params (habit_id, name, unit, direction, daily_target, position, param_type)
                            VALUES (?, ?, ?, ?::habit_direction, ?, ?, ?::param_type)
                            """.trimIndent(),
                            id, p.name, p.unit, p.direction?.value, p.dailyTarget, i, p.paramType.value
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
                        deleted_at   = CASE WHEN ?::habit_status = 'deleted' AND status <> 'deleted' THEN now() ELSE deleted_at END,
                        paused_until = CASE WHEN ?::habit_status <> 'paused' THEN NULL ELSE paused_until END
                    WHERE id = ? AND user_id = ?
                    """.trimIndent(),
                    habit.name, habit.type.value, habit.dailyTarget, habit.unit, habit.direction?.value,
                    habit.status.value, habit.logOnly,
                    habit.status.value, habit.status.value, habit.status.value,
                    habit.id, habit.userId
                )
            )
        }
        return habit
    }

    /**
     * Pauses an active habit. [until] is the auto-resume moment, or null for an indefinite pause.
     * No-op (returns false) if the habit isn't currently active.
     */
    fun pauseHabit(habitId: Long, userId: Long, until: OffsetDateTime?): Boolean =
        sessionOf(DatabaseService.dataSource).use { session ->
            session.update(
                queryOf(
                    """
                    UPDATE habits
                    SET status = 'paused', paused_at = now(), paused_until = ?
                    WHERE id = ? AND user_id = ? AND status = 'active'
                    """.trimIndent(),
                    until, habitId, userId
                )
            ) > 0
        }

    /** Flips every paused habit whose deadline has passed back to active, returning the ones resumed. */
    fun autoResumeExpired(): List<ResumedHabit> =
        sessionOf(DatabaseService.dataSource).use { session ->
            session.run(
                queryOf(
                    """
                    WITH resumed AS (
                        UPDATE habits
                        SET status = 'active', paused_until = NULL
                        WHERE status = 'paused' AND paused_until IS NOT NULL AND paused_until <= now()
                        RETURNING user_id, name
                    )
                    SELECT r.user_id, r.name, us.language AS lang
                    FROM resumed r
                    LEFT JOIN user_settings us ON us.user_id = r.user_id
                    """.trimIndent()
                ).map { ResumedHabit(it.long("user_id"), it.string("name"), it.stringOrNull("lang")) }.asList
            )
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
                           h.user_id, h.name, r.reminder_time, r.reminder_days,
                           us.timezone AS tz, us.language AS lang
                    FROM habit_reminders r
                    JOIN habits h ON h.id = r.habit_id
                    JOIN user_settings us ON us.user_id = h.user_id
                    LEFT JOIN checkins c
                        ON c.reminder_id = r.id
                       AND c.check_date = CASE WHEN r.reminder_time >= 1440
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
     * are inserted as `pending` and returned so the caller can
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
                               (d::date
                                + CASE WHEN r.reminder_time >= 1440 THEN INTERVAL '1 day' ELSE INTERVAL '0' END
                                + (r.reminder_time % 1440) * INTERVAL '1 minute'
                               ) AT TIME ZONE us.timezone AS fired_at,
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
                          AND (d::date
                               + CASE WHEN r.reminder_time >= 1440 THEN INTERVAL '1 day' ELSE INTERVAL '0' END
                               + (r.reminder_time % 1440) * INTERVAL '1 minute'
                              ) AT TIME ZONE us.timezone >= h.created_at
                          AND (d::date
                               + CASE WHEN r.reminder_time >= 1440 THEN INTERVAL '1 day' ELSE INTERVAL '0' END
                               + (r.reminder_time % 1440) * INTERVAL '1 minute'
                              ) AT TIME ZONE us.timezone < now() - INTERVAL '1 minute'
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
                        -- Scheduled habits have no param: the status row carries a NULL param_id.
                        INSERT INTO checkin_values (checkin_id, param_id, status, value)
                        SELECT ie.id,
                               NULL,
                               CASE WHEN now() - m.fired_at > INTERVAL '24 hours'
                                    THEN 'skip'::checkin_status
                                    ELSE 'pending'::checkin_status END,
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
