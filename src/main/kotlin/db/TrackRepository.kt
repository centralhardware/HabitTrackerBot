package db

import services.DatabaseService
import dto.Track
import dto.TrackParam
import dto.TrackType
import dto.RawDue
import dto.RawMissed
import dto.ResumedTrack
import dto.toTrack
import dto.toTrackParam
import dto.toTrackReminder
import dto.toRawDue
import dto.toRawMissed
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import java.time.OffsetDateTime

object TrackRepository {

    fun find(trackId: Long, userId: Long): Track? =
        listRawActive(userId).firstOrNull { it.id == trackId }

    fun listActive(userId: Long): List<Track> = listRawActive(userId)

    private fun listRawActive(userId: Long): List<Track> {
        return sessionOf(DatabaseService.dataSource).use { session ->
            val tracks = session.run(
                queryOf(
                    """
                    SELECT h.id, h.user_id, h.name, h.track_type, h.daily_target,
                           h.unit, h.direction, h.status, h.log_only, h.allow_adhoc
                    FROM tracks h
                    WHERE h.user_id = ? AND h.status <> 'deleted'
                    ORDER BY h.created_at
                    """.trimIndent(),
                    userId
                ).map { it.toTrack() }.asList
            )
            if (tracks.isEmpty()) return@use tracks

            val remindersByTrack = session.run(
                queryOf(
                    """
                    SELECT r.id, r.track_id, r.reminder_time, r.reminder_days
                    FROM track_reminders r
                    JOIN tracks h ON h.id = r.track_id
                    WHERE h.user_id = ? AND h.status <> 'deleted'
                    ORDER BY r.reminder_time
                    """.trimIndent(),
                    userId
                ).map { it.long("track_id") to it.toTrackReminder() }.asList
            ).groupBy({ it.first }, { it.second })

            val paramsByTrack = session.run(
                queryOf(
                    """
                    SELECT p.id, p.track_id, p.name, p.unit, p.direction, p.daily_target, p.position, p.param_type, p.timer_phase
                    FROM track_params p
                    JOIN tracks h ON h.id = p.track_id
                    WHERE h.user_id = ? AND h.status <> 'deleted' AND p.deleted = false
                    ORDER BY p.track_id, p.position, p.id
                    """.trimIndent(),
                    userId
                ).map { it.long("track_id") to it.toTrackParam() }.asList
            ).groupBy({ it.first }, { it.second })

            tracks.map { h ->
                val params = paramsByTrack[h.id].orEmpty()
                // Single-field quantity tracks keep their metadata on the param row; hoist it onto
                // the track so every single-field track looks the same to callers.
                val hoisted = if ((h.type == TrackType.QUANTITY || h.type == TrackType.TIMER) && params.size == 1) {
                    val p = params[0]
                    h.copy(
                        unit = h.unit ?: p.unit,
                        dailyTarget = h.dailyTarget ?: p.dailyTarget,
                        direction = h.direction ?: p.direction,
                    )
                } else h
                hoisted.copy(
                    reminders = remindersByTrack[h.id].orEmpty(),
                    params = params,
                )
            }
        }
    }

    fun upsert(track: Track): Track =
        if (track.id == 0L) insert(track) else update(track)

    /** Inserts a track, its reminders and its params (every track carries >=1 param). */
    private fun insert(track: Track): Track {
        return using(sessionOf(DatabaseService.dataSource, returnGeneratedKey = true)) { session ->
            session.transaction { tx ->
                val id = tx.updateAndReturnGeneratedKey(
                    queryOf(
                        """
                        INSERT INTO tracks (user_id, name, track_type, daily_target, unit, direction, status, log_only, allow_adhoc)
                        VALUES (?, ?, ?::track_type, ?, ?, ?::track_direction, ?::track_status, ?, ?)
                        """.trimIndent(),
                        track.userId, track.name, track.type.value,
                        track.dailyTarget, track.unit, track.direction?.value,
                        track.status.value, track.logOnly, track.allowAdHoc
                    )
                ) ?: error("Failed to insert track")

                track.reminders.forEach { rem ->
                    tx.execute(
                        queryOf(
                            "INSERT INTO track_reminders (track_id, reminder_time, reminder_days) VALUES (?, ?, ?::int[])",
                            id, rem.offsetMinutes, rem.days.toPgArray()
                        )
                    )
                }

                val params = track.params.ifEmpty {
                    when (track.type) {
                        // Quantity tracks get a numeric service param; timer tracks store their
                        // elapsed minutes on the same kind of single numeric param.
                        TrackType.QUANTITY, TrackType.TIMER -> listOf(TrackParam(id = 0, paramType = dto.ParamType.NUMBER))
                        // Check tracks need no param: ad-hoc events are bare checkins rows, and
                        // scheduled slots keep their done/skip status on the checkins row.
                        TrackType.CHECK -> emptyList()
                    }
                }
                val savedParams = params.mapIndexed { i, p ->
                    val pid = tx.updateAndReturnGeneratedKey(
                        queryOf(
                            """
                            INSERT INTO track_params (track_id, name, unit, direction, daily_target, position, param_type, timer_phase)
                            VALUES (?, ?, ?, ?::track_direction, ?, ?, ?::param_type, ?)
                            """.trimIndent(),
                            id, p.name, p.unit, p.direction?.value, p.dailyTarget, i, p.paramType.value, p.timerPhase?.value
                        )
                    ) ?: error("Failed to insert track param")
                    p.copy(id = pid, trackId = id, position = i)
                }

                track.copy(id = id, params = savedParams)
            }
        }
    }

    private fun update(track: Track): Track {
        sessionOf(DatabaseService.dataSource).use { session ->
            session.update(
                queryOf(
                    """
                    UPDATE tracks
                    SET name         = ?,
                        track_type   = ?::track_type,
                        daily_target = ?,
                        unit         = ?,
                        direction    = ?::track_direction,
                        status       = ?::track_status,
                        log_only     = ?,
                        allow_adhoc  = ?,
                        paused_at    = CASE WHEN ?::track_status = 'paused'  AND status <> 'paused'  THEN now() ELSE paused_at  END,
                        deleted_at   = CASE WHEN ?::track_status = 'deleted' AND status <> 'deleted' THEN now() ELSE deleted_at END,
                        paused_until = CASE WHEN ?::track_status <> 'paused' THEN NULL ELSE paused_until END
                    WHERE id = ? AND user_id = ?
                    """.trimIndent(),
                    track.name, track.type.value, track.dailyTarget, track.unit, track.direction?.value,
                    track.status.value, track.logOnly, track.allowAdHoc,
                    track.status.value, track.status.value, track.status.value,
                    track.id, track.userId
                )
            )
        }
        return track
    }

    /**
     * Pauses an active track. [until] is the auto-resume moment, or null for an indefinite pause.
     * No-op (returns false) if the track isn't currently active.
     */
    fun pauseTrack(trackId: Long, userId: Long, until: OffsetDateTime?): Boolean =
        sessionOf(DatabaseService.dataSource).use { session ->
            session.update(
                queryOf(
                    """
                    UPDATE tracks
                    SET status = 'paused', paused_at = now(), paused_until = ?
                    WHERE id = ? AND user_id = ? AND status = 'active'
                    """.trimIndent(),
                    until, trackId, userId
                )
            ) > 0
        }

    /** Flips every paused track whose deadline has passed back to active, returning the ones resumed. */
    fun autoResumeExpired(): List<ResumedTrack> =
        sessionOf(DatabaseService.dataSource).use { session ->
            session.run(
                queryOf(
                    """
                    WITH resumed AS (
                        UPDATE tracks
                        SET status = 'active', paused_until = NULL
                        WHERE status = 'paused' AND paused_until IS NOT NULL AND paused_until <= now()
                        RETURNING user_id, name
                    )
                    SELECT r.user_id, r.name, us.language AS lang
                    FROM resumed r
                    LEFT JOIN user_settings us ON us.user_id = r.user_id
                    """.trimIndent()
                ).map { ResumedTrack(it.long("user_id"), it.string("name"), it.stringOrNull("lang")) }.asList
            )
        }

    fun findTrackIdByReminder(reminderId: Long, userId: Long): Long? {
        return sessionOf(DatabaseService.dataSource).use { session ->
            session.run(
                queryOf(
                    """
                    SELECT h.id
                    FROM track_reminders r
                    JOIN tracks h ON h.id = r.track_id
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
                    SELECT r.id AS reminder_id,
                           h.id AS track_id,
                           h.track_type,
                           h.user_id,
                           h.name,
                           r.reminder_time,
                           r.reminder_days,
                           us.timezone AS tz,
                           us.language AS lang
                    FROM track_reminders r
                    JOIN tracks h ON h.id = r.track_id
                    JOIN user_settings us ON us.user_id = h.user_id
                    LEFT JOIN checkins c
                        ON c.reminder_id = r.id
                       AND c.check_date = CASE WHEN r.reminder_time >= 1440
                           THEN (now() AT TIME ZONE us.timezone)::date - 1
                           ELSE (now() AT TIME ZONE us.timezone)::date
                       END
                    WHERE h.status = 'active'
                      AND us.timezone IS NOT NULL
                      AND (h.track_type <> 'check' OR c.id IS NULL)
                    """.trimIndent()
                ).map { it.toRawDue() }.asList
            )
        }
    }

    /**
     * Backfills checkin rows for every scheduled reminder slot that has fired
     * but has no checkin yet (going back to track creation). Slots whose firing
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
                        SELECT r.id AS reminder_id,
                               r.track_id,
                               h.user_id,
                               d::date AS missed_date,
                               (d::date
                                + CASE WHEN r.reminder_time >= 1440 THEN INTERVAL '1 day' ELSE INTERVAL '0' END
                                + (r.reminder_time % 1440) * INTERVAL '1 minute'
                               ) AT TIME ZONE us.timezone AS fired_at,
                               us.language AS lang_code,
                               h.name AS track_name,
                               r.reminder_time
                        FROM track_reminders r
                        JOIN tracks h ON h.id = r.track_id
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
                          AND h.track_type = 'check'
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
                        INSERT INTO checkins (user_id, check_date, reminder_id, track_id, comment, checked_at)
                        SELECT user_id,
                               missed_date,
                               reminder_id,
                               track_id,
                               NULL,
                               CASE WHEN now() - fired_at > INTERVAL '24 hours'
                                    THEN now()
                                    ELSE NULL END
                        FROM missed
                        ON CONFLICT (reminder_id, check_date)
                            WHERE reminder_id IS NOT NULL DO NOTHING
                        RETURNING id, reminder_id, check_date
                    ),
                    ins_values AS (
                        -- Scheduled tracks have no param: the status row carries a NULL param_id.
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
                    SELECT m.reminder_id,
                           m.track_id,
                           m.user_id,
                           m.track_name AS name,
                           m.reminder_time,
                           m.lang_code AS lang,
                           m.missed_date
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

    /**
     * Soft-deletes a single track param owned by [userId]: the row (and its checkin_values) stay so
     * historical records still resolve its name, but it's hidden from the active track. Refuses
     * (returns false) when it's the track's only live param, since every track must keep >=1.
     */
    fun deleteParam(paramId: Long, userId: Long): Boolean =
        sessionOf(DatabaseService.dataSource).use { session ->
            session.update(
                queryOf(
                    """
                    UPDATE track_params p
                    SET deleted = true, deleted_at = now()
                    FROM tracks h
                    WHERE p.id = ? AND p.track_id = h.id AND h.user_id = ?
                      AND p.deleted = false
                      AND (SELECT count(*) FROM track_params p2
                           WHERE p2.track_id = p.track_id AND p2.deleted = false) > 1
                    """.trimIndent(),
                    paramId, userId
                )
            ) > 0
        }

    /** Renders weekday list as a Postgres int[] literal, or null (= every day) when empty. */
    private fun List<Int>.toPgArray(): String? =
        takeIf { it.isNotEmpty() }?.joinToString(",", "{", "}")
}
