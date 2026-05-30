package services

import db.ReminderMessageRepository
import dto.SentReminderMessage
import java.time.LocalDate

object ReminderMessageService {

    fun remember(userId: Long, messageId: Long, reminderId: Long, date: LocalDate, text: String) =
        ReminderMessageRepository.save(userId, messageId, reminderId, date, text)

    fun forCheckIn(reminderId: Long, date: LocalDate): List<SentReminderMessage> =
        ReminderMessageRepository.findFor(reminderId, date)

    fun forget(reminderId: Long, date: LocalDate) =
        ReminderMessageRepository.deleteFor(reminderId, date)
}
