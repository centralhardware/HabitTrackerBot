package commands

import CheckInService
import HabitService
import Keyboards
import Strings
import UserSettingsService
import dev.inmo.tgbotapi.extensions.api.send.sendMessage
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContext
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onCommand
import senderLang
import senderUserId
import java.time.LocalDate

fun BehaviourContext.registerCheckInCommand() {
    onCommand("checkin") { message ->
        val userId = message.senderUserId() ?: return@onCommand
        val lang = message.senderLang()
        val tz = UserSettingsService.getTimezone(userId)
        if (tz == null) {
            sendMessage(message.chat.id, Strings.tzRequiredCheckIn(lang))
            return@onCommand
        }
        val today = LocalDate.now(tz)
        val yesterday = today.minusDays(1)
        val scheduled = CheckInService.pendingCheckIns(userId, yesterday, today)
        val counters = HabitService.listActive(userId)
            .filter { it.status == HabitService.Status.ACTIVE && it.type == HabitService.Type.COUNTER }

        if (scheduled.isEmpty() && counters.isEmpty()) {
            sendMessage(message.chat.id, Strings.nothingToCheckIn(lang))
            return@onCommand
        }

        sendMessage(message.chat.id, Strings.pendingCheckIns(lang))

        scheduled.forEach { item ->
            val time = item.reminderTime.format(Keyboards.TIME_FMT)
            sendMessage(
                chatId = message.chat.id,
                text = "⏳ ${item.date} $time — ${item.name}",
                replyMarkup = Keyboards.checkIn(item.reminderId, item.date, lang)
            )
        }

        counters.forEach { habit ->
            val current = CheckInService.todayCount(habit.id, today)
            sendMessage(
                chatId = message.chat.id,
                text = Strings.counterLine(lang, habit, current, today),
                replyMarkup = Keyboards.logPlus(habit.id, today, lang)
            )
        }
    }
}
