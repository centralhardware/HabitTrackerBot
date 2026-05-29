import dev.inmo.kslog.common.KSLog
import dev.inmo.kslog.common.info
import dev.inmo.tgbotapi.extensions.api.send.sendMessage
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContext
import dev.inmo.tgbotapi.types.toChatId
import dto.DueReminder
import dto.HabitType

object ReminderScheduler {

    suspend fun BehaviourContext.sendDueReminders() {
        CheckInService.autoSkipOverdue()
        HabitService.backfillMissedScheduled().forEach { reminder ->
            deliver(reminder, markPending = false, withDate = true)
        }
        HabitService.findDue().forEach { reminder ->
            deliver(reminder, markPending = true, withDate = false)
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
            val text = "$datePrefix⏳ ${reminder.reminderTime.format(Keyboards.TIME_FMT)} — ${reminder.name}"
            val keyboard = when (reminder.habitType) {
                HabitType.SCHEDULED ->
                    Keyboards.checkIn(reminder.reminderId, reminder.userDate, lang)
                HabitType.COUNTER ->
                    Keyboards.logPlus(reminder.habitId, reminder.userDate, lang)
                HabitType.QUANTITY ->
                    Keyboards.logQuantity(reminder.habitId, reminder.userDate, lang)
            }
            sendMessage(
                chatId = reminder.userId.toChatId(),
                text = text,
                replyMarkup = keyboard
            )
        }.onFailure { e ->
            KSLog.info("Failed to send reminder to ${reminder.userId}: ${e.message}")
        }
    }
}
