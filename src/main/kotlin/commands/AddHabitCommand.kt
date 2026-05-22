package commands

import HabitService
import Keyboards
import UserSettingsService
import dev.inmo.tgbotapi.extensions.api.send.sendMessage
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContext
import dev.inmo.tgbotapi.extensions.behaviour_builder.expectations.waitTextMessage
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onCommand
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import senderUserId
import java.time.LocalTime
import java.time.format.DateTimeParseException

fun BehaviourContext.registerAddHabitCommand() {
    onCommand("addhabit") { message ->
        val userId = message.senderUserId() ?: return@onCommand

        if (UserSettingsService.getTimezone(userId) == null) {
            sendMessage(
                message.chat.id,
                "Set your timezone first with /tz <IANA name>, e.g. /tz Europe/Moscow"
            )
            return@onCommand
        }

        val chatLong = message.chat.id.chatId.long

        sendMessage(message.chat.id, "Send the habit name:")
        val nameText = waitTextMessage()
            .filter { it.chat.id.chatId.long == chatLong }
            .first()
            .content
            .text
            .trim()

        if (nameText.isBlank() || nameText.startsWith("/")) {
            sendMessage(message.chat.id, "Cancelled.")
            return@onCommand
        }

        sendMessage(
            message.chat.id,
            "Send one or more reminder times (HH:MM), space-separated. Example: 09:00 21:00"
        )
        val timesText = waitTextMessage()
            .filter { it.chat.id.chatId.long == chatLong }
            .first()
            .content
            .text
            .trim()

        if (timesText.startsWith("/")) {
            sendMessage(message.chat.id, "Cancelled.")
            return@onCommand
        }

        val tokens = timesText.split(Regex("\\s+")).filter { it.isNotBlank() }
        val times = try {
            tokens.map { LocalTime.parse(it, Keyboards.TIME_FMT) }.distinct().sorted()
        } catch (_: DateTimeParseException) {
            sendMessage(message.chat.id, "Invalid time format. Use HH:MM, e.g. 09:00.")
            return@onCommand
        }

        if (times.isEmpty()) {
            sendMessage(message.chat.id, "No times provided.")
            return@onCommand
        }

        val habit = HabitService.addHabit(userId, nameText, times)
        val timesView = habit.reminders.joinToString(", ") { it.format(Keyboards.TIME_FMT) }
        sendMessage(message.chat.id, "Added: \"${habit.name}\" at $timesView")
    }
}
