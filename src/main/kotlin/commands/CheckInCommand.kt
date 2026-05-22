package commands

import CheckInService
import Keyboards
import UserSettingsService
import dev.inmo.tgbotapi.extensions.api.send.sendMessage
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContext
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onCommand
import senderUserId
import java.time.LocalDate

fun BehaviourContext.registerCheckInCommand() {
    onCommand("checkin") { message ->
        val userId = message.senderUserId() ?: return@onCommand
        val tz = UserSettingsService.getTimezone(userId)
        if (tz == null) {
            sendMessage(message.chat.id, "Set your timezone first with /tz <IANA name>.")
            return@onCommand
        }
        val today = LocalDate.now(tz)
        val items = CheckInService.todaysCheckIns(userId, today)
        if (items.isEmpty()) {
            sendMessage(message.chat.id, "No habits scheduled for today.")
            return@onCommand
        }

        sendMessage(message.chat.id, "Check-ins for $today:")
        items.forEach { item ->
            val time = item.reminderTime.format(Keyboards.TIME_FMT)
            val statusIcon = when (item.status) {
                "done" -> "✅"
                "skip" -> "❌"
                else -> "⏳"
            }
            sendMessage(
                chatId = message.chat.id,
                text = "$statusIcon $time — ${item.name}",
                replyMarkup = Keyboards.checkIn(item.habitId, item.reminderTime, today)
            )
        }
    }
}
