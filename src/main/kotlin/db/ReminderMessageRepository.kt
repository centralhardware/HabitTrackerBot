package db

import services.DatabaseService
import dto.SentReminderMessage
import dto.toSentReminderMessage
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import java.time.LocalDate

/**
 * Tracks the scheduled-reminder messages we send for each (reminder_id, check_date)
 * so they can all be rewritten once the check-in is resolved. See V19.
 */
object ReminderMessageRepository {

    fun save(userId: Long, messageId: Long, reminderId: Long, checkDate: LocalDate, text: String) {
        using(sessionOf(DatabaseService.dataSource)) { session ->
            session.update(
                queryOf(
                    """
                    INSERT INTO reminder_messages (user_id, message_id, reminder_id, check_date, text)
                    VALUES (?, ?, ?, ?, ?)
                    """.trimIndent(),
                    userId, messageId, reminderId, checkDate, text
                )
            )
        }
    }

    fun findFor(reminderId: Long, checkDate: LocalDate): List<SentReminderMessage> {
        return sessionOf(DatabaseService.dataSource).use { session ->
            session.run(
                queryOf(
                    """
                    SELECT user_id, message_id, text
                    FROM reminder_messages
                    WHERE reminder_id = ? AND check_date = ?
                    ORDER BY id
                    """.trimIndent(),
                    reminderId, checkDate
                ).map { it.toSentReminderMessage() }.asList
            )
        }
    }

    fun deleteFor(reminderId: Long, checkDate: LocalDate): Int {
        return using(sessionOf(DatabaseService.dataSource)) { session ->
            session.update(
                queryOf(
                    "DELETE FROM reminder_messages WHERE reminder_id = ? AND check_date = ?",
                    reminderId, checkDate
                )
            )
        }
    }
}
