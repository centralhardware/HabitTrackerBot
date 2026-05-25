package db

import DatabaseService
import dto.UserSettings
import dto.toUserSettings
import kotliquery.queryOf
import kotliquery.sessionOf

object UserSettingsRepository {

    fun find(userId: Long): UserSettings? {
        return sessionOf(DatabaseService.dataSource).use { session ->
            session.run(
                queryOf(
                    "SELECT user_id, timezone, language FROM user_settings WHERE user_id = ?",
                    userId
                ).map { it.toUserSettings() }.asSingle
            )
        }
    }

    fun upsert(settings: UserSettings) {
        sessionOf(DatabaseService.dataSource).use { session ->
            session.execute(
                queryOf(
                    """
                    INSERT INTO user_settings (user_id, timezone, language)
                    VALUES (?, ?, ?)
                    ON CONFLICT (user_id) DO UPDATE
                        SET timezone = EXCLUDED.timezone,
                            language = EXCLUDED.language
                    """.trimIndent(),
                    settings.userId, settings.timezone, settings.language
                )
            )
        }
    }
}
