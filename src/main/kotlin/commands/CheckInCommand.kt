package commands

import services.CheckInService
import services.HabitService
import services.ReminderMessageService
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
        val counters = active.filter { it.type == HabitType.COUNTER }

        if (scheduled.isEmpty() && counters.isEmpty()) {
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
            // Track this message too, so resolving the check-in settles it like a scheduled reminder.
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
    }
}
