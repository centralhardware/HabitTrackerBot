package db

import dto.CalendarSubscription
import dto.toCalendarSubscription
import kotliquery.queryOf
import kotliquery.sessionOf
import services.DatabaseService

object CalendarTokenRepository {

    /** Creates or replaces the user's calendar token, preserving content flags if a row already exists. */
    fun upsertToken(userId: Long, tokenHash: ByteArray): CalendarSubscription {
        sessionOf(DatabaseService.dataSource).use { session ->
            session.update(
                queryOf(
                    """
                    INSERT INTO calendar_tokens (user_id, token_hash)
                    VALUES (?, ?)
                    ON CONFLICT (user_id) DO UPDATE
                       SET token_hash = EXCLUDED.token_hash,
                           created_at = now(),
                           last_used_at = NULL
                    """.trimIndent(),
                    userId, tokenHash
                )
            )
        }
        return find(userId) ?: error("Upserted calendar token not found")
    }

    fun updateContent(userId: Long, includeCheckins: Boolean, includeReminders: Boolean): Boolean =
        sessionOf(DatabaseService.dataSource).use { session ->
            session.update(
                queryOf(
                    "UPDATE calendar_tokens SET include_checkins = ?, include_reminders = ? WHERE user_id = ?",
                    includeCheckins, includeReminders, userId
                )
            ) > 0
        }

    fun delete(userId: Long): Boolean =
        sessionOf(DatabaseService.dataSource).use { session ->
            session.update(queryOf("DELETE FROM calendar_tokens WHERE user_id = ?", userId)) > 0
        }

    fun find(userId: Long): CalendarSubscription? =
        sessionOf(DatabaseService.dataSource).use { session ->
            session.run(
                queryOf(
                    "SELECT * FROM calendar_tokens WHERE user_id = ?", userId
                ).map { it.toCalendarSubscription() }.asSingle
            )
        }

    fun findByHash(tokenHash: ByteArray): CalendarSubscription? =
        sessionOf(DatabaseService.dataSource).use { session ->
            session.run(
                queryOf(
                    "SELECT * FROM calendar_tokens WHERE token_hash = ?", tokenHash
                ).map { it.toCalendarSubscription() }.asSingle
            )
        }

    fun touchLastUsed(tokenHash: ByteArray) {
        sessionOf(DatabaseService.dataSource).use { session ->
            session.update(
                queryOf("UPDATE calendar_tokens SET last_used_at = now() WHERE token_hash = ?", tokenHash)
            )
        }
    }
}
