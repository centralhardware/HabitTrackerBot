package db

import dto.CalendarCheckin
import dto.CalendarReminder
import dto.toCalendarCheckin
import dto.toCalendarReminder
import kotliquery.queryOf
import kotliquery.sessionOf
import services.DatabaseService
import java.time.LocalDate

/** Read-only queries that feed the iCal calendar: a user's logged check-ins and active reminders. */
object CalendarFeedRepository {

    /**
     * One row per `checkin_values` value (and one bare row per counter event) for the user's
     * non-deleted habits, dated on or after [since]. The service groups these by checkin id.
     */
    fun checkinsSince(userId: Long, since: LocalDate): List<CalendarCheckin> =
        sessionOf(DatabaseService.dataSource).use { session ->
            session.run(
                queryOf(
                    """
                    SELECT e.id AS checkin_id, h.name, h.habit_type, e.check_date, v.status, read_param_value(v.value, v.value_id, v.value_num) AS value,
                           p.param_type, p.name AS param_name, p.unit, e.comment, e.checked_at
                    FROM checkins e
                    JOIN habits h ON h.id = e.habit_id AND h.status <> 'deleted'
                    LEFT JOIN checkin_values v ON v.checkin_id = e.id
                    LEFT JOIN habit_params p ON p.id = v.param_id
                    WHERE e.user_id = ?
                      AND e.deleted = false
                      AND e.check_date >= ?
                    ORDER BY e.check_date, e.id, p.position
                    """.trimIndent(),
                    userId, since
                ).map { it.toCalendarCheckin() }.asList
            )
        }

    fun reminders(userId: Long): List<CalendarReminder> =
        sessionOf(DatabaseService.dataSource).use { session ->
            session.run(
                queryOf(
                    """
                    SELECT r.id, h.name, r.reminder_time, r.reminder_days
                    FROM habit_reminders r
                    JOIN habits h ON h.id = r.habit_id
                    WHERE h.user_id = ?
                      AND h.status = 'active'
                    ORDER BY r.reminder_time
                    """.trimIndent(),
                    userId
                ).map { it.toCalendarReminder() }.asList
            )
        }
}
