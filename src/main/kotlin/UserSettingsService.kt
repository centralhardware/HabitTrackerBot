import kotliquery.queryOf
import kotliquery.sessionOf
import java.time.ZoneId

object UserSettingsService {

    fun getTimezone(userId: Long): ZoneId? {
        val stored = sessionOf(DatabaseService.dataSource).use { session ->
            session.run(
                queryOf("SELECT timezone FROM user_settings WHERE user_id = ?", userId)
                    .map { it.string("timezone") }
                    .asSingle
            )
        } ?: return null
        return runCatching { ZoneId.of(stored) }.getOrNull()
    }

    fun setTimezone(userId: Long, tz: ZoneId) {
        sessionOf(DatabaseService.dataSource).use { session ->
            session.execute(
                queryOf(
                    """
                    INSERT INTO user_settings (user_id, timezone)
                    VALUES (?, ?)
                    ON CONFLICT (user_id) DO UPDATE
                        SET timezone = EXCLUDED.timezone
                    """.trimIndent(),
                    userId,
                    tz.id
                )
            )
        }
    }
}
