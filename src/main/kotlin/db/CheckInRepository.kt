package db

import services.DatabaseService
import dto.CheckinEvent
import dto.CheckinStatus
import dto.CheckinValue
import dto.CheckinValueRow
import dto.DeletableCheckin
import dto.FieldValue
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
    fun upsertScheduledValue(event: CheckinEvent, status: CheckinStatus): Boolean {
        require(event.reminderId != null) { "scheduled upsert requires reminderId" }
        // A pending row has no check-in yet, so checked_at stays NULL;
        // it's stamped only when the slot is actually resolved (done / skip).
        val checkedAt = if (status == CheckinStatus.PENDING) null else Instant.now()
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
                    -- Scheduled habits have no param, so the status row carries a NULL param_id
                    -- (deduped one-per-event by checkin_values_noparam_uniq).
                    INSERT INTO checkin_values (checkin_id, param_id, status, value)
                    SELECT id, NULL, ?::checkin_status, NULL
                    FROM upsert_event
                    ON CONFLICT (checkin_id) WHERE param_id IS NULL
                    DO UPDATE SET status = EXCLUDED.status
                    """.trimIndent(),
                    event.userId, event.checkDate, event.reminderId, event.habitId, checkedAt,
                    status.value
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
            values.forEach { add(it.paramId); add(it.status?.value); add(it.value?.asString) }
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
     * Appends extra value rows to an existing check-in — used to attach a timer's before/after
     * annotation fields to the elapsed-time event written when the timer stopped. No-op when
     * [values] is empty. A param already present on the check-in keeps its existing row.
     */
    fun addValues(checkinId: Long, values: List<CheckinValue>) {
        if (values.isEmpty()) return
        val valuesSql = values.joinToString(", ") { "(?, ?, ?::checkin_status, ?)" }
        val params = buildList<Any?> {
            values.forEach { add(checkinId); add(it.paramId); add(it.status?.value); add(it.value?.asString) }
        }
        using(sessionOf(DatabaseService.dataSource)) { session ->
            session.update(
                queryOf(
                    """
                    INSERT INTO checkin_values (checkin_id, param_id, status, value)
                    VALUES $valuesSql
                    -- Match the partial unique index checkin_values_param_uniq (V30), defined
                    -- WHERE param_id IS NOT NULL. Without repeating that predicate Postgres can't
                    -- infer the index and the whole INSERT errors out — which silently dropped
                    -- every timer before/after annotation field. These rows always have a param_id.
                    ON CONFLICT (checkin_id, param_id) WHERE param_id IS NOT NULL DO NOTHING
                    """.trimIndent(),
                    *params.toTypedArray()
                )
            )
        }
    }

    /**
     * Inserts a bare event with no values (a counter tap). Returns the new `checkins.id`.
     * The CTE-wrapped RETURNING mirrors [insertEventWithValues] to dodge the PG JDBC 42.7.4
     * top-level-RETURNING crash.
     */
    fun insertEvent(event: CheckinEvent): Long {
        return using(sessionOf(DatabaseService.dataSource)) { session ->
            session.run(
                queryOf(
                    """
                    WITH new_event AS (
                        INSERT INTO checkins (user_id, check_date, reminder_id, habit_id, comment, checked_at)
                        VALUES (?, ?, NULL, ?, ?, now())
                        RETURNING id
                    )
                    SELECT id FROM new_event
                    """.trimIndent(),
                    event.userId, event.checkDate, event.habitId, event.comment
                ).map { it.long("id") }.asSingle
            ) ?: 0L
        }
    }

    /**
     * Marks a scheduled slot pending (creating its checkin row) and, in the same statement,
     * skips every older still-pending check-in of the same habit — the previous reminder
     * occurrences the user never resolved. Returns the (reminder, date) of each flipped row,
     * for message updates.
     *
     * A slot's firing moment is `check_date + reminder_time` in the user's timezone; the new
     * slot's firing moment is the cut-off — older pending siblings that fired before it get
     * flipped to `skip` and stamped `checked_at = now()` as their skip moment. Replaces the
     * old standalone 24h overdue scan: the skip now happens exactly when the next reminder
     * fires.
     */
    fun markPendingSkippingPrevious(event: CheckinEvent): List<dto.ResolvedCheckin> {
        require(event.reminderId != null) { "scheduled pending requires reminderId" }
        return using(sessionOf(DatabaseService.dataSource)) { session ->
            session.run(
                queryOf(
                    """
                    WITH upsert_event AS (
                        INSERT INTO checkins (user_id, check_date, reminder_id, habit_id, comment, checked_at)
                        VALUES (?, ?, ?, ?, NULL, NULL)
                        ON CONFLICT (reminder_id, check_date)
                            WHERE reminder_id IS NOT NULL
                        DO UPDATE SET checked_at = COALESCE(EXCLUDED.checked_at, checkins.checked_at)
                        RETURNING id, user_id, habit_id, reminder_id, check_date
                    ),
                    upsert_value AS (
                        -- Scheduled habits have no param, so the status row carries a NULL param_id.
                        INSERT INTO checkin_values (checkin_id, param_id, status, value)
                        SELECT id, NULL, 'pending'::checkin_status, NULL
                        FROM upsert_event
                        ON CONFLICT (checkin_id) WHERE param_id IS NULL
                        DO UPDATE SET status = EXCLUDED.status
                    ),
                    new_slot AS (
                        SELECT ue.id, ue.habit_id,
                               (ue.check_date::date
                                + CASE WHEN r.reminder_time >= 1440 THEN INTERVAL '1 day' ELSE INTERVAL '0' END
                                + (r.reminder_time % 1440) * INTERVAL '1 minute'
                               ) AT TIME ZONE us.timezone AS fired_at
                        FROM upsert_event ue
                        JOIN habit_reminders r ON r.id = ue.reminder_id
                        JOIN user_settings us ON us.user_id = ue.user_id
                        WHERE us.timezone IS NOT NULL
                    ),
                    flipped AS (
                        UPDATE checkin_values v
                        SET status = 'skip'
                        FROM checkins e
                        JOIN habit_reminders r2 ON r2.id = e.reminder_id
                        JOIN user_settings us2 ON us2.user_id = e.user_id
                        CROSS JOIN new_slot ns
                        WHERE v.checkin_id = e.id
                          AND v.status = 'pending'
                          AND e.id <> ns.id
                          AND e.habit_id = ns.habit_id
                          AND e.deleted = false
                          AND us2.timezone IS NOT NULL
                          AND (e.check_date::date
                               + CASE WHEN r2.reminder_time >= 1440 THEN INTERVAL '1 day' ELSE INTERVAL '0' END
                               + (r2.reminder_time % 1440) * INTERVAL '1 minute'
                              ) AT TIME ZONE us2.timezone < ns.fired_at
                        RETURNING v.checkin_id, e.reminder_id AS reminder_id, e.check_date AS check_date
                    ),
                    touch AS (
                        UPDATE checkins SET checked_at = now()
                        WHERE id IN (SELECT checkin_id FROM flipped)
                    )
                    SELECT reminder_id, check_date FROM flipped
                    """.trimIndent(),
                    event.userId, event.checkDate, event.reminderId, event.habitId
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
                    SELECT e.id AS checkin_id, v.param_id, p.param_type, p.timer_phase, e.check_date, e.reminder_id,
                           v.status, v.value, e.comment, r.reminder_time, e.checked_at
                    FROM checkins e
                    -- LEFT JOIN: counter events have no checkin_values row, but still
                    -- need to appear (one row per event) so they're counted.
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
                            value = if (pt == ParamType.NUMBER) rawValue?.toDoubleOrNull()?.let { FieldValue.Numeric(it) }
                            else rawValue?.let { FieldValue.Text(it) },
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

    /**
     * Inserts or updates a single param [value] on a check-in the user owns. The edit path may set a
     * param the entry doesn't carry yet (e.g. a book name added after the fact), so this is an upsert,
     * not a plain UPDATE. The INSERT is gated on ownership via the SELECT; the ON CONFLICT predicate
     * matches the partial unique index checkin_values_param_uniq (V30, WHERE param_id IS NOT NULL).
     */
    fun upsertCheckinValue(checkinId: Long, userId: Long, paramId: Long, value: String): Boolean {
        return using(sessionOf(DatabaseService.dataSource)) { session ->
            session.update(
                queryOf(
                    """
                    INSERT INTO checkin_values (checkin_id, param_id, status, value)
                    SELECT e.id, ?, 'done'::checkin_status, ?
                    FROM checkins e
                    WHERE e.id = ?
                      AND e.user_id = ?
                      AND e.deleted = false
                    ON CONFLICT (checkin_id, param_id) WHERE param_id IS NOT NULL
                    DO UPDATE SET value = EXCLUDED.value
                    """.trimIndent(),
                    paramId, value, checkinId, userId
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
                      AND v.status = 'pending'
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
