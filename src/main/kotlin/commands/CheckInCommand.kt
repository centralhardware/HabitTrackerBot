package commands

import services.CheckInService
import services.HabitService
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
import dto.HabitStatus
import dto.HabitType
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
        val active = HabitService.listActive(data.userId).filter { it.status == HabitStatus.ACTIVE }
        val counters = active.filter { it.type == HabitType.CHECK && it.allowAdHoc }
        // Running timers surface here too, so the user can stop them mid check-in.
        val timersById = active.filter { it.type == HabitType.TIMER }.associateBy { it.id }
        val runningTimers = TimerService.running(data.userId).filter { it.habitId in timersById }

        if (scheduled.isEmpty() && counters.isEmpty() && runningTimers.isEmpty()) {
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

        counters.forEach { habit ->
            val current = CheckInService.counterCountOn(habit.id, today)
            sendMessage(
                chatId = message.chat.id,
                text = Strings.counterLine(data.lang, habit, current, today),
                replyMarkup = Keyboards.logPlus(habit.id, today, data.lang)
            )
        }

        runningTimers.forEach { rt ->
            val habit = timersById.getValue(rt.habitId)
            val elapsed = TimerService.elapsedSeconds(rt)
            val todaySeconds = CheckInService.timerSecondsOn(habit.id, today)
            val sent = sendMessage(
                chatId = message.chat.id,
                text = Strings.timerLine(data.lang, habit, running = true, elapsed, todaySeconds, rt.paused),
                replyMarkup = Keyboards.timerControl(habit.id, running = true, today, data.lang, rt.paused)
            )
            // Let the background ticker keep this message's elapsed time live.
            TimerService.setMessage(habit.id, data.userId, sent.messageId.long)
        }
    }
}
