package commands

import services.CheckInService
import services.TrackService
import services.ReminderMessageService
import services.TimerService
import Keyboards
import Strings
import lang
import tz
import userId
import db.CheckInRepository
import dev.inmo.tgbotapi.extensions.api.send.sendMessage
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContext
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onCommand
import dto.TrackStatus
import dto.TrackType
import java.time.LocalDate

fun BehaviourContext.registerCheckInCommand() {
    onCommand("checkin") { message ->
        if (data.tz == null) {
            sendMessage(message.chat.id, Strings.tzRequiredCheckIn(data.lang))
            return@onCommand
        }
        val today = LocalDate.now(data.tz)
        val yesterday = today.minusDays(1)
        val scheduled = CheckInRepository.pendingCheckIns(data.userId, yesterday, today)
        val active = TrackService.listActive(data.userId).filter { it.status == TrackStatus.ACTIVE }
        // Running timers surface here too, so the user can stop them mid check-in.
        val timersById = active.filter { it.type == TrackType.TIMER }.associateBy { it.id }
        val runningTimers = TimerService.running(data.userId).filter { it.trackId in timersById }

        if (scheduled.isEmpty() && runningTimers.isEmpty()) {
            sendMessage(message.chat.id, Strings.nothingToCheckIn(data.lang))
            return@onCommand
        }

        sendMessage(message.chat.id, Strings.pendingCheckIns(data.lang))

        scheduled.forEach { item ->
            val text = "⏳ ${item.date} ${Strings.formatDisplayTime(item.offsetMinutes)} — ${item.name}"
            val sent = sendMessage(
                chatId = message.chat.id,
                text = text,
                replyMarkup = Keyboards.checkIn(item.reminderId, item.date, data.lang)
            )
            ReminderMessageService.remember(data.userId, sent.messageId.long, item.reminderId, item.date, text)
        }

        runningTimers.forEach { rt ->
            val track = timersById.getValue(rt.trackId)
            val elapsed = TimerService.elapsedSeconds(rt)
            val todaySeconds = CheckInService.timerSecondsOn(track.id, today)
            val sent = sendMessage(
                chatId = message.chat.id,
                text = Strings.timerLine(data.lang, track, running = true, elapsed, todaySeconds, rt.paused),
                replyMarkup = Keyboards.timerControl(track.id, running = true, today, data.lang, rt.paused)
            )
            // Let the background ticker keep this message's elapsed time live.
            TimerService.setMessage(track.id, data.userId, sent.messageId.long)
        }
    }
}
