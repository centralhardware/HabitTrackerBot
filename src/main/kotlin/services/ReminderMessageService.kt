package services

import db.ReminderMessageRepository
import dto.SentReminderMessage
import java.time.LocalDate

/**
 * Remembers sent scheduled-reminder messages and hands them back when their check-in
 * is resolved, so every duplicate message for the same (reminder, date) can be updated.
 */
object ReminderMessageService {

    fun remember(userId: Long, messageId: Long, reminderId: Long, date: LocalDate, text: String) =
        ReminderMessageRepository.save(userId, messageId, reminderId, date, text)

    fun forCheckIn(reminderId: Long, date: LocalDate): List<SentReminderMessage> =
        ReminderMessageRepository.findFor(reminderId, date)

    fun forget(reminderId: Long, date: LocalDate) =
        ReminderMessageRepository.deleteFor(reminderId, date)
}
