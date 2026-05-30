package db

import services.DatabaseService
import dto.DueUser
import dto.toDueUser
import kotliquery.queryOf
import kotliquery.sessionOf

object SchedulerRepository {

    fun findDueWeeklyUsers(dow: Int, hour: Int): List<DueUser> {
        return sessionOf(DatabaseService.dataSource).use { session ->
            session.run(
                queryOf(
                    """
                    SELECT us.user_id,
                           (now() AT TIME ZONE us.timezone)::date AS today,
                           us.language
                    FROM user_settings us
                    WHERE us.timezone IS NOT NULL
                      AND EXTRACT(DOW    FROM (now() AT TIME ZONE us.timezone))::int = ?
                      AND EXTRACT(HOUR   FROM (now() AT TIME ZONE us.timezone))::int = ?
                      AND EXTRACT(MINUTE FROM (now() AT TIME ZONE us.timezone))::int = 0
                    """.trimIndent(),
                    dow, hour
                ).map { it.toDueUser() }.asList
            )
        }
    }
}
