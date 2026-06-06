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

    /** How often, in seconds, a running timer's live message is repainted. */
    private const val TICK_SECONDS = 10L

    /**
     * Repaints the live message of every running timer with its current elapsed time. Run once a
     * second: each timer is repainted only when its own elapsed time crosses a [TICK_SECONDS]
     * boundary, so the displayed value advances on the timer's own grid (0, 10, 20…) regardless of
     * when the second-aligned cron fires. A deleted/old/unchanged message is swallowed per-timer
     * and the loop carries on.
     */
    suspend fun BehaviourContext.tickRunningTimers() {
        TimerService.dueTicks().forEach { t ->
            runCatching {
                val elapsed = TimerService.elapsedSeconds(t.startedAt)
                if (elapsed.toLong() % TICK_SECONDS != 0L) return@forEach
                val lang = t.langCode?.let { runCatching { Lang.valueOf(it) }.getOrNull() } ?: Lang.EN
                val zone = t.tzId?.let { runCatching { ZoneId.of(it) }.getOrNull() }
                val date = if (zone != null) LocalDate.now(zone) else LocalDate.now()
                val habit = Habit(id = t.habitId, name = t.name, type = HabitType.TIMER)
                editMessageText(
                    chatId = t.userId.toChatId(),
                    messageId = MessageId(t.messageId),
                    text = Strings.timerLine(lang, habit, running = true, elapsed, todaySeconds = 0.0),
                    replyMarkup = Keyboards.timerControl(t.habitId, running = true, date, lang),
                )
            }.onFailure { e ->
                KSLog.info("Failed to tick timer ${t.habitId} for ${t.userId}: ${e.message}")
            }
        }
    }
}
