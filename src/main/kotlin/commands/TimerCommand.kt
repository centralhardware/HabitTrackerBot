package commands

import services.CheckInService
import services.HabitService
import services.TimerService
import Keyboards
import Strings
import dev.inmo.tgbotapi.extensions.api.send.sendMessage
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContext
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onCommand
import dto.HabitStatus
import dto.HabitType
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
        val timers = HabitService.listActive(data.userId)
            .filter { it.status == HabitStatus.ACTIVE && it.type == HabitType.TIMER }
        if (timers.isEmpty()) {
            sendMessage(message.chat.id, Strings.noTimers(data.lang))
            return@onCommand
        }

        val running = TimerService.running(data.userId).associateBy { it.habitId }

        sendMessage(message.chat.id, Strings.yourTimers(data.lang))
        timers.forEach { habit ->
            val timer = running[habit.id]
            val elapsed = timer?.let { TimerService.elapsedMinutes(it.startedAt) } ?: 0.0
            val todayMinutes = CheckInService.timerMinutesOn(habit.id, today)
            sendMessage(
                chatId = message.chat.id,
                text = Strings.timerLine(data.lang, habit, timer != null, elapsed, todayMinutes),
                replyMarkup = Keyboards.timerControl(habit.id, timer != null, today, data.lang)
            )
        }
    }
}
