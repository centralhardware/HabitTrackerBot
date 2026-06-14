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
     * How often, in seconds, a running timer's live message is repainted, stepped by how long it
     * has already been running: every second for the first half-minute, every 10 seconds up to the
     * 10-minute mark, then once a minute. The boundaries are multiples of the next step so the
     * displayed value never skips a grid line when the cadence changes.
     */
    private fun tickInterval(elapsedSeconds: Long): Long = when {
        elapsedSeconds < 30 -> 1L
        elapsedSeconds < 600 -> 10L
        else -> 60L
    }

    /**
     * Repaints the live message of every running timer with its current elapsed time. Run once a
     * second: each timer is repainted only when its own elapsed time crosses its current
     * [tickInterval] boundary, so the displayed value advances on the timer's own grid regardless of
     * when the second-aligned cron fires. A deleted/old/unchanged message is swallowed per-timer
     * and the loop carries on.
     */
    suspend fun BehaviourContext.tickRunningTimers() {
        TimerService.dueTicks().forEach { t ->
            runCatching {
                val elapsed = TimerService.elapsedSeconds(t.startedAt)
                if (elapsed.toLong() % tickInterval(elapsed.toLong()) != 0L) return@forEach
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
