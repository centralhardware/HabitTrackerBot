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
        val yesterday = today.minusDays(1)
        val items = CheckInService.pendingCheckIns(userId, yesterday, today)
        if (items.isEmpty()) {
            sendMessage(message.chat.id, "Nothing to check in.")
            return@onCommand
        }

        sendMessage(message.chat.id, "Pending check-ins:")
        items.forEach { item ->
            val time = item.reminderTime.format(Keyboards.TIME_FMT)
            val dayLabel = when (item.date) {
                today -> "today"
                yesterday -> "yesterday"
                else -> item.date.toString()
            }
            sendMessage(
                chatId = message.chat.id,
                text = "⏳ $dayLabel $time — ${item.name}",
                replyMarkup = Keyboards.checkIn(item.reminderId, item.date)
            )
        }
    }
}
