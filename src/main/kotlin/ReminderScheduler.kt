import dev.inmo.kslog.common.KSLog
import dev.inmo.kslog.common.info
import dev.inmo.tgbotapi.extensions.api.send.sendMessage
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContext
import dev.inmo.tgbotapi.types.toChatId

object ReminderScheduler {

    suspend fun BehaviourContext.runOnce() {
        CheckInService.autoSkipOverdue()
        val due = HabitService.findDue()
        if (due.isEmpty()) return

        due.forEach { reminder ->
            runCatching {
                sendMessage(
                    chatId = reminder.userId.toChatId(),
                    text = "⏳ ${reminder.reminderTime.format(Keyboards.TIME_FMT)} — ${reminder.name}",
                    replyMarkup = Keyboards.checkIn(reminder.reminderId, reminder.userDate)
                )
            }.onFailure { e ->
                KSLog.info("Failed to send reminder to ${reminder.userId}: ${e.message}")
            }
        }
    }
}
