import dev.inmo.kslog.common.KSLog
import dev.inmo.kslog.common.info
import dev.inmo.tgbotapi.extensions.api.send.sendMessage
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContext
import dev.inmo.tgbotapi.types.toChatId
import dto.DueReminder
import dto.HabitType
import dto.CheckinStatus
import services.CheckInService
import services.HabitService
import services.ReminderMessageService

object ReminderScheduler {

    suspend fun BehaviourContext.sendDueReminders() {
        CheckInService.autoSkipOverdue().forEach { resolved ->
            resolveCheckInMessages(resolved.reminderId, resolved.checkDate, CheckinStatus.SKIP)
        }
        HabitService.backfillMissedScheduled().forEach { reminder ->
            deliver(reminder, markPending = false, withDate = true)
        }
        HabitService.findDue().forEach { reminder ->
            deliver(reminder, markPending = true, withDate = reminder.offsetMinutes >= 1440)
        }
    }

    private suspend fun BehaviourContext.deliver(
        reminder: DueReminder,
        markPending: Boolean,
        withDate: Boolean
    ) {
        runCatching {
            if (markPending && reminder.habitType == HabitType.SCHEDULED) {
                CheckInService.markPending(reminder.habitId, reminder.userId, reminder.reminderId, reminder.userDate)
            }
            val lang = reminder.langCode?.let { runCatching { Lang.valueOf(it) }.getOrNull() } ?: Lang.EN
            val datePrefix = if (withDate) "📅 ${reminder.userDate} " else ""
            val text = "$datePrefix⏳ ${Strings.formatDisplayTime(reminder.offsetMinutes)} — ${reminder.name}"
            val keyboard = when (reminder.habitType) {
                HabitType.SCHEDULED ->
                    Keyboards.checkIn(reminder.reminderId, reminder.userDate, lang)
                HabitType.COUNTER ->
                    Keyboards.logPlus(reminder.habitId, reminder.userDate, lang)
                HabitType.QUANTITY -> null
            }
            val sent = sendMessage(
                chatId = reminder.userId.toChatId(),
                text = text,
                replyMarkup = keyboard
            )
            if (reminder.habitType == HabitType.SCHEDULED) {
                ReminderMessageService.remember(
                    reminder.userId, sent.messageId.long, reminder.reminderId, reminder.userDate, text
                )
            }
        }.onFailure { e ->
            KSLog.info("Failed to send reminder to ${reminder.userId}: ${e.message}")
        }
    }
}
