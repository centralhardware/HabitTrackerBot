package commands

import services.CheckInService
import services.TrackService
import services.TimerService
import Keyboards
import Strings
import dev.inmo.tgbotapi.extensions.api.send.sendMessage
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContext
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onCommand
import dto.TrackStatus
import dto.TrackType
import lang
import tz
import userId
import java.time.LocalDate

fun BehaviourContext.registerTimerCommand() {
    onCommand("timer") { message ->
        if (data.tz == null) {
            sendMessage(message.chat.id, Strings.tzRequiredTimer(data.lang))
            return@onCommand
        }
        val today = LocalDate.now(data.tz)
        val timers = TrackService.listActive(data.userId)
            .filter { it.status == TrackStatus.ACTIVE && it.type == TrackType.TIMER }
        if (timers.isEmpty()) {
            sendMessage(message.chat.id, Strings.noTimers(data.lang))
            return@onCommand
        }

        val running = TimerService.running(data.userId).associateBy { it.trackId }

        sendMessage(message.chat.id, Strings.yourTimers(data.lang))
        timers.forEach { track ->
            val timer = running[track.id]
            val elapsed = timer?.let { TimerService.elapsedSeconds(it) } ?: 0.0
            val paused = timer?.paused == true
            val todaySeconds = CheckInService.timerSecondsOn(track.id, today)
            val sent = sendMessage(
                chatId = message.chat.id,
                text = Strings.timerLine(data.lang, track, timer != null, elapsed, todaySeconds, paused),
                replyMarkup = Keyboards.timerControl(track.id, timer != null, today, data.lang, paused)
            )
            // Make this the message the background ticker keeps live for a running timer.
            if (timer != null) TimerService.setMessage(track.id, data.userId, sent.messageId.long)
        }
    }
}
