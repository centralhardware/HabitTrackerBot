import kotliquery.queryOf
import kotliquery.sessionOf
import java.time.ZoneId

object UserSettingsService {

    fun getTimezone(userId: Long): ZoneId? {
        val stored = sessionOf(DatabaseService.dataSource).use { session ->
            session.run(
                queryOf("SELECT timezone FROM user_settings WHERE user_id = ?", userId)
                    .map { it.stringOrNull("timezone") }
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

    fun getLanguage(userId: Long): Lang? {
        val stored = sessionOf(DatabaseService.dataSource).use { session ->
            session.run(
                queryOf("SELECT language FROM user_settings WHERE user_id = ?", userId)
                    .map { it.stringOrNull("language") }
                    .asSingle
            )
        } ?: return null
        return runCatching { Lang.valueOf(stored) }.getOrNull()
    }

    fun touchLanguage(userId: Long, lang: Lang) {
        sessionOf(DatabaseService.dataSource).use { session ->
            session.execute(
                queryOf(
                    """
                    INSERT INTO user_settings (user_id, language)
                    VALUES (?, ?)
                    ON CONFLICT (user_id) DO UPDATE
                        SET language = EXCLUDED.language
                        WHERE user_settings.language IS NULL
                    """.trimIndent(),
                    userId,
                    lang.name
                )
            )
        }
    }

    fun setLanguage(userId: Long, lang: Lang) {
        sessionOf(DatabaseService.dataSource).use { session ->
            session.execute(
                queryOf(
                    """
                    INSERT INTO user_settings (user_id, language)
                    VALUES (?, ?)
                    ON CONFLICT (user_id) DO UPDATE
                        SET language = EXCLUDED.language
                    """.trimIndent(),
                    userId,
                    lang.name
                )
            )
        }
    }
}
