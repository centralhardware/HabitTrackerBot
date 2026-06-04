package db

import services.DatabaseService
import dto.CheckinEvent
import dto.CheckinStatus
import dto.CheckinValue
import dto.CheckinValueRow
import dto.DeletableCheckin
import dto.ParamType
import dto.PendingCheckIn
import dto.toCheckinValueRow
import dto.toResolvedCheckin
import dto.toPendingCheckIn
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import java.time.Instant
import java.time.LocalDate

object CheckInRepository {

    /**
     * Upsert a scheduled-reminder check-in: keyed on (reminder_id, check_date).
     * Used for pending-row creation and for the done/skip click.
     *
     * Implemented as a single CTE so we avoid the `returnGeneratedKey` +
     * explicit `RETURNING` + `asSingle` combo that crashes PG JDBC 42.7.4
     * with "No results were returned by the query."
     */
    fun upsertScheduledValue(event: CheckinEvent, value: CheckinValue): Boolean {
        require(event.reminderId != null) { "scheduled upsert requires reminderId" }
        // A pending row (status == null) has no check-in yet, so checked_at stays NULL;
        // it's stamped only when the slot is actually resolved (done / skip).
        val checkedAt = value.status?.let { Instant.now() }
        return using(sessionOf(DatabaseService.dataSource)) { session ->
            val written = session.update(
                queryOf(
                    """
                    WITH upsert_event AS (
                        INSERT INTO checkins (user_id, check_date, reminder_id, habit_id, comment, checked_at)
                        VALUES (?, ?, ?, ?, NULL, ?)
                        ON CONFLICT (reminder_id, check_date)
                            WHERE reminder_id IS NOT NULL
                        DO UPDATE SET checked_at = COALESCE(EXCLUDED.checked_at, checkins.checked_at)
                        RETURNING id
                    )
                    INSERT INTO checkin_values (checkin_id, param_id, status, value)
                    SELECT id, ?, ?::checkin_status, NULL
                    FROM upsert_event
                    ON CONFLICT (checkin_id, param_id)
                    DO UPDATE SET status = EXCLUDED.status
                    """.trimIndent(),
                    event.userId, event.checkDate, event.reminderId, event.habitId, checkedAt,
                    value.paramId, value.status?.value
                )
            )
            written > 0
        }
    }

    /**
     * Insert a non-scheduled event (counter/quantity) with one or more values.
     * Each call creates a fresh event row — there is no per-day uniqueness.
     * Returns the new `checkins.id`, or 0 when there is nothing to write.
     */
    fun insertEventWithValues(event: CheckinEvent, values: List<CheckinValue>): Long {
        if (values.isEmpty()) return 0
        // A single CTE: insert the event, fan its id out to every value row, and read the id
        // back through a plain top-level SELECT. Keeping the RETURNING inside the CTE avoids
        // the `updateAndReturnGeneratedKey` / top-level RETURNING path that crashes PG JDBC
        // 42.7.4 (see upsertScheduledValue) and stays atomic in one round-trip.
        val valuesSql = values.joinToString(", ") { "(?, ?::checkin_status, ?)" }
        val params = buildList<Any?> {
            add(event.userId); add(event.checkDate); add(event.habitId); add(event.comment)
            values.forEach { add(it.paramId); add(it.status?.value); add(it.dbValue) }
        }
        return using(sessionOf(DatabaseService.dataSource)) { session ->
            session.run(
                queryOf(
                    """
                    WITH new_event AS (
                        INSERT INTO checkins (user_id, check_date, reminder_id, habit_id, comment, checked_at)
                        VALUES (?, ?, NULL, ?, ?, now())
                        RETURNING id
                    ),
                    new_values AS (
                        INSERT INTO checkin_values (checkin_id, param_id, status, value)
                        SELECT ne.id, t.param_id, t.status, t.value
                        FROM new_event ne
                        CROSS JOIN (VALUES $valuesSql) AS t(param_id, status, value)
                    )
                    SELECT id FROM new_event
                    """.trimIndent(),
                    *params.toTypedArray()
                ).map { it.long("id") }.asSingle
            ) ?: 0L
        }
    }

    /**
     * Insert a non-scheduled event with no per-param values — used by counter habits, where the
     * event row itself is the unit being counted (see [loadForHabit]'s LEFT JOIN). Each call
     * creates a fresh event row; there is no per-day uniqueness. Returns the new `checkins.id`.
     */
    fun insertEvent(event: CheckinEvent): Long {
        return using(sessionOf(DatabaseService.dataSource)) { session ->
            session.run(
                queryOf(
                    """
                    INSERT INTO checkins (user_id, check_date, reminder_id, habit_id, comment, checked_at)
                    VALUES (?, ?, NULL, ?, ?, now())
                    RETURNING id
                    """.trimIndent(),
                    event.userId, event.checkDate, event.habitId, event.comment
                ).map { it.long("id") }.asSingle
            ) ?: 0L
        }
    }

    /**
     * Skips pending scheduled values whose slot fired before [threshold], returning the
     * (reminder, date) of each flipped row. The overdue test uses the slot's firing moment
     * (`check_date + reminder_time` in the user's timezone), since pending rows no longer
     * carry a `checked_at`; the flip then stamps `checked_at = now()` as the skip moment.
     */
    fun markPendingAsSkip(threshold: Instant): List<dto.ResolvedCheckin> {
        return sessionOf(DatabaseService.dataSource).use { session ->
            session.run(
                queryOf(
                    """
                    WITH flipped AS (
                        UPDATE checkin_values v
                        SET status = 'skip'
                        FROM checkins e
                        JOIN habit_reminders r ON r.id = e.reminder_id
                        JOIN user_settings us ON us.user_id = e.user_id
                        WHERE v.checkin_id = e.id
                          AND v.status IS NULL
                          AND e.reminder_id IS NOT NULL
                          AND e.deleted = false
                          AND us.timezone IS NOT NULL
                          AND (e.check_date::date
                               + CASE WHEN r.reminder_time >= 1440 THEN INTERVAL '1 day' ELSE INTERVAL '0' END
                               + (r.reminder_time % 1440) * INTERVAL '1 minute'
                              ) AT TIME ZONE us.timezone < ?
                        RETURNING v.checkin_id, e.reminder_id AS reminder_id, e.check_date AS check_date
                    ),
                    touch AS (
                        UPDATE checkins SET checked_at = now()
                        WHERE id IN (SELECT checkin_id FROM flipped)
                    )
                    SELECT reminder_id, check_date FROM flipped
                    """.trimIndent(),
                    threshold
                ).map { it.toResolvedCheckin() }.asList
            )
        }
    }

    /**
     * Loads the full check-in history of a single habit as raw rows (one per param value).
     * All per-habit stats (counts, sums, streaks, weekly totals) are computed over this list
     * in Kotlin (see CheckinAnalytics) instead of via specialized aggregate queries.
     */
    fun loadForHabit(habitId: Long): List<CheckinValueRow> {
        return sessionOf(DatabaseService.dataSource).use { session ->
            session.run(
                queryOf(
                    """
                    SELECT e.id AS checkin_id, v.param_id, p.param_type, e.check_date, e.reminder_id,
                           v.status, v.value, e.comment, r.reminder_time
                    FROM checkins e
                    -- LEFT JOIN: counter events carry no checkin_values row (the event is the count),
                    -- so they'd be dropped by an inner join. param_id/status/value are NULL for them.
                    LEFT JOIN checkin_values v ON v.checkin_id = e.id
                    LEFT JOIN habit_params p ON p.id = v.param_id
                    LEFT JOIN habit_reminders r ON r.id = e.reminder_id
                    WHERE e.habit_id = ?
                      AND e.deleted = false
                    ORDER BY e.check_date, r.reminder_time NULLS FIRST, e.id, v.param_id
                    """.trimIndent(),
                    habitId
                ).map { it.toCheckinValueRow() }.asList
            )
        }
    }

    /**
     * Loads a quantity/text check-in event (not-yet-deleted) with all its values, scoped to [userId].
     * Scheduled reminder check-ins (reminder_id set) and counter events (value null) are excluded.
     * Returns null when no such event exists.
     */
    fun loadEventForDelete(checkinId: Long, userId: Long): DeletableCheckin? {
        return sessionOf(DatabaseService.dataSource).use { session ->
            val rows = session.run(
                queryOf(
                    """
                    SELECT e.habit_id, e.check_date, e.comment,
                           v.param_id, p.param_type, v.status, v.value
                    FROM checkins e
                    JOIN checkin_values v ON v.checkin_id = e.id
                    JOIN habit_params p ON p.id = v.param_id
                    WHERE e.id = ?
                      AND e.user_id = ?
                      AND e.reminder_id IS NULL
                      AND e.deleted = false
                      AND v.value IS NOT NULL
                    """.trimIndent(),
                    checkinId, userId
                ).map { row ->
                    val pt = ParamType.parse(row.stringOrNull("param_type"))
                    val rawValue = row.stringOrNull("value")
                    Triple(
                        Pair(row.long("habit_id"), row.localDate("check_date")),
                        row.stringOrNull("comment"),
                        CheckinValue(
                            paramId = row.long("param_id"),
                            status = row.stringOrNull("status")
                                ?.let { s -> CheckinStatus.entries.firstOrNull { it.value == s } },
                            quantity = if (pt == ParamType.NUMBER) rawValue?.toDoubleOrNull() else null,
                            textValue = if (pt == ParamType.TEXT) rawValue else null,
                        )
                    )
                }.asList
            )
            if (rows.isEmpty()) null
            else DeletableCheckin(
                checkinId,
                rows.first().first.first,
                rows.first().first.second,
                rows.map { it.third },
                rows.first().second,
            )
        }
    }

    fun updateCheckinComment(checkinId: Long, userId: Long, comment: String?): Boolean {
        return using(sessionOf(DatabaseService.dataSource)) { session ->
            session.update(
                queryOf(
                    "UPDATE checkins SET comment = ? WHERE id = ? AND user_id = ? AND deleted = false",
                    comment, checkinId, userId
                )
            ) > 0
        }
    }

    /** Updates the stored [value] string of a single param row within a check-in. */
    fun updateCheckinValue(checkinId: Long, userId: Long, paramId: Long, value: String): Boolean {
        return using(sessionOf(DatabaseService.dataSource)) { session ->
            session.update(
                queryOf(
                    """
                    UPDATE checkin_values v
                    SET value = ?
                    FROM checkins e
                    WHERE v.checkin_id = e.id
                      AND e.id = ?
                      AND e.user_id = ?
                      AND e.deleted = false
                      AND v.param_id = ?
                      AND v.value IS NOT NULL
                    """.trimIndent(),
                    value, checkinId, userId, paramId
                )
            ) > 0
        }
    }

    /**
     * Soft-deletes a quantity/text check-in event dated on or after [notBefore]; true when a row was
     * flipped. Mirrors [loadEventForDelete]'s guards so the delete itself enforces the bounds.
     */
    fun softDeleteEvent(checkinId: Long, userId: Long, notBefore: LocalDate): Boolean {
        return using(sessionOf(DatabaseService.dataSource)) { session ->
            session.update(
                queryOf(
                    """
                    UPDATE checkins e
                    SET deleted = true
                    WHERE e.id = ?
                      AND e.user_id = ?
                      AND e.reminder_id IS NULL
                      AND e.deleted = false
                      AND e.check_date >= ?
                      AND EXISTS (
                          SELECT 1 FROM checkin_values v
                          WHERE v.checkin_id = e.id AND v.value IS NOT NULL
                      )
                    """.trimIndent(),
                    checkinId, userId, notBefore
                )
            ) > 0
        }
    }

    fun pendingCheckIns(userId: Long, fromDate: LocalDate, toDate: LocalDate): List<PendingCheckIn> {
        return sessionOf(DatabaseService.dataSource).use { session ->
            session.run(
                queryOf(
                    """
                    SELECT e.reminder_id, h.name, r.reminder_time, e.check_date
                    FROM checkins e
                    JOIN habits h ON h.id = e.habit_id
                    JOIN habit_reminders r ON r.id = e.reminder_id
                    JOIN checkin_values v ON v.checkin_id = e.id
                    WHERE h.user_id = ?
                      AND h.status = 'active'
                      AND v.status IS NULL
                      AND e.deleted = false
                      AND e.check_date BETWEEN ?::date AND ?::date
                    ORDER BY e.check_date, r.reminder_time
                    """.trimIndent(),
                    userId, fromDate, toDate
                ).map { it.toPendingCheckIn() }.asList
            )
        }
    }
}
