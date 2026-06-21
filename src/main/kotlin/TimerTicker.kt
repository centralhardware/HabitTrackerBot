import dev.inmo.kslog.common.KSLog
import dev.inmo.kslog.common.info
import dev.inmo.tgbotapi.extensions.api.edit.text.editMessageText
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContext
import dev.inmo.tgbotapi.types.MessageId
import dev.inmo.tgbotapi.types.toChatId
import dto.Track
import dto.TrackType
import services.TimerService
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

object TimerTicker {

    /**
     * How often, in seconds, a running timer's live message is repainted, stepped by how long the
     * current live segment has been running: every second for the first half-minute, every 10
     * seconds up to the 10-minute mark, then once a minute. The boundaries are multiples of the next
     * step so the displayed value never skips a grid line when the cadence changes. The cadence is
     * driven by the live segment (time since the last start/resume), not the total elapsed time, so
     * a resumed timer repaints every second again as if freshly started.
     */
    private fun tickInterval(segmentSeconds: Long): Long = when {
        segmentSeconds < 30 -> 1L
        segmentSeconds < 600 -> 10L
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
                // A paused timer is frozen — nothing to repaint until the user resumes or stops it.
                if (t.paused) return@forEach
                val elapsed = TimerService.elapsedSeconds(t.startedAt, t.accumulatedSeconds, t.pausedAt)
                // Cadence follows the live segment, so a resume (which resets started_at to now)
                // restarts the per-second repaint as if the timer were freshly started.
                val segment = Duration.between(t.startedAt, Instant.now()).seconds
                if (segment % tickInterval(segment) != 0L) return@forEach
                val lang = t.langCode?.let { runCatching { Lang.valueOf(it) }.getOrNull() } ?: Lang.EN
                val zone = t.tzId?.let { runCatching { ZoneId.of(it) }.getOrNull() }
                val date = if (zone != null) LocalDate.now(zone) else LocalDate.now()
                val track = Track(id = t.trackId, name = t.name, type = TrackType.TIMER)
                editMessageText(
                    chatId = t.userId.toChatId(),
                    messageId = MessageId(t.messageId),
                    text = Strings.timerLine(lang, track, running = true, elapsed, todaySeconds = 0.0),
                    replyMarkup = Keyboards.timerControl(t.trackId, running = true, date, lang),
                )
            }.onFailure { e ->
                KSLog.info("Failed to tick timer ${t.trackId} for ${t.userId}: ${e.message}")
            }
        }
    }
}
