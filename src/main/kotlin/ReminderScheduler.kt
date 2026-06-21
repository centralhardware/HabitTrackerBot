import dev.inmo.kslog.common.KSLog
import dev.inmo.kslog.common.info
import dev.inmo.tgbotapi.extensions.api.send.sendMessage
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContext
import dev.inmo.tgbotapi.types.toChatId
import dto.DueReminder
import dto.TrackType
import dto.CheckinStatus
import services.CheckInService
import services.TrackService
import services.ReminderMessageService

object ReminderScheduler {

    suspend fun BehaviourContext.sendDueReminders() {
        TrackService.backfillMissedScheduled().forEach { reminder ->
            deliver(reminder, markPending = false, withDate = true)
        }
        TrackService.findDue().forEach { reminder ->
            deliver(reminder, markPending = true, withDate = reminder.offsetMinutes >= 1440)
        }
    }

    private suspend fun BehaviourContext.deliver(
        reminder: DueReminder,
        markPending: Boolean,
        withDate: Boolean
    ) {
        runCatching {
            if (markPending && reminder.trackType == TrackType.CHECK) {
                // Creating this slot's pending row also skips the previous unresolved
                // occurrence of the same track; settle those messages now.
                CheckInService.markPending(reminder.trackId, reminder.userId, reminder.reminderId, reminder.userDate)
                    .forEach { resolved ->
                        resolveCheckInMessages(resolved.reminderId, resolved.checkDate, CheckinStatus.SKIP)
                    }
            }
            val lang = reminder.langCode?.let { runCatching { Lang.valueOf(it) }.getOrNull() } ?: Lang.EN
            val datePrefix = if (withDate) "📅 ${reminder.userDate} " else ""
            val text = "$datePrefix⏳ ${Strings.formatDisplayTime(reminder.offsetMinutes)} — ${reminder.name}"
            val keyboard = when (reminder.trackType) {
                // A check track's reminder is a markable slot now (done/skip), never a "+1" nudge.
                TrackType.CHECK ->
                    Keyboards.checkIn(reminder.reminderId, reminder.userDate, lang)
                TrackType.TIMER ->
                    Keyboards.timerControl(reminder.trackId, running = false, reminder.userDate, lang)
                TrackType.QUANTITY -> null
            }
            val sent = sendMessage(
                chatId = reminder.userId.toChatId(),
                text = text,
                replyMarkup = keyboard
            )
            if (reminder.trackType == TrackType.CHECK) {
                ReminderMessageService.remember(
                    reminder.userId, sent.messageId.long, reminder.reminderId, reminder.userDate, text
                )
            }
        }.onFailure { e ->
            KSLog.info("Failed to send reminder to ${reminder.userId}: ${e.message}")
        }
    }
}
