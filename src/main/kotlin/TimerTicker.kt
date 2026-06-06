import dev.inmo.kslog.common.KSLog
import dev.inmo.kslog.common.info
import dev.inmo.tgbotapi.extensions.api.edit.text.editMessageText
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContext
import dev.inmo.tgbotapi.types.MessageId
import dev.inmo.tgbotapi.types.toChatId
import dto.Habit
import dto.HabitType
import services.TimerService
import java.time.LocalDate
import java.time.ZoneId

object TimerTicker {

    /**
     * Repaints the live message of every running timer with its current elapsed time. Run once a
     * minute. The text only changes when the whole-minute count advances, so a "message not
     * modified" edit (or a deleted/old message) is swallowed per-timer and the loop carries on.
     */
    suspend fun BehaviourContext.tickRunningTimers() {
        TimerService.dueTicks().forEach { t ->
            runCatching {
                val lang = t.langCode?.let { runCatching { Lang.valueOf(it) }.getOrNull() } ?: Lang.EN
                val zone = t.tzId?.let { runCatching { ZoneId.of(it) }.getOrNull() }
                val date = if (zone != null) LocalDate.now(zone) else LocalDate.now()
                val elapsed = TimerService.elapsedMinutes(t.startedAt)
                val habit = Habit(id = t.habitId, name = t.name, type = HabitType.TIMER)
                editMessageText(
                    chatId = t.userId.toChatId(),
                    messageId = MessageId(t.messageId),
                    text = Strings.timerLine(lang, habit, running = true, elapsed, todayMinutes = 0.0),
                    replyMarkup = Keyboards.timerControl(t.habitId, running = true, date, lang),
                )
            }.onFailure { e ->
                KSLog.info("Failed to tick timer ${t.habitId} for ${t.userId}: ${e.message}")
            }
        }
    }
}
