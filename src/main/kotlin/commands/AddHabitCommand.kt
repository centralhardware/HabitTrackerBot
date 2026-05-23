package commands

import HabitService
import Keyboards
import Strings
import UserSettingsService
import dev.inmo.tgbotapi.extensions.api.send.sendMessage
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContext
import dev.inmo.tgbotapi.extensions.behaviour_builder.expectations.waitTextMessage
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onCommand
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import senderLang
import senderUserId
import java.time.LocalTime
import java.time.format.DateTimeParseException

fun BehaviourContext.registerAddHabitCommand() {
    onCommand("addhabit") { message ->
        val userId = message.senderUserId() ?: return@onCommand
        val lang = message.senderLang()

        if (UserSettingsService.getTimezone(userId) == null) {
            sendMessage(message.chat.id, Strings.tzRequiredAddHabit(lang))
            return@onCommand
        }

        val chatLong = message.chat.id.chatId.long

        sendMessage(message.chat.id, Strings.sendHabitName(lang))
        val nameText = waitTextMessage()
            .filter { it.chat.id.chatId.long == chatLong }
            .first()
            .content
            .text
            .trim()

        if (nameText.isBlank() || nameText.startsWith("/")) {
            sendMessage(message.chat.id, Strings.cancelled(lang))
            return@onCommand
        }

        sendMessage(message.chat.id, Strings.sendTimes(lang))
        val timesText = waitTextMessage()
            .filter { it.chat.id.chatId.long == chatLong }
            .first()
            .content
            .text
            .trim()

        if (timesText.startsWith("/")) {
            sendMessage(message.chat.id, Strings.cancelled(lang))
            return@onCommand
        }

        val tokens = timesText.split(Regex("\\s+")).filter { it.isNotBlank() }
        val times = try {
            tokens.map { LocalTime.parse(it, Keyboards.TIME_FMT) }.distinct().sorted()
        } catch (_: DateTimeParseException) {
            sendMessage(message.chat.id, Strings.invalidTime(lang))
            return@onCommand
        }

        if (times.isEmpty()) {
            sendMessage(message.chat.id, Strings.noTimes(lang))
            return@onCommand
        }

        val habit = HabitService.addHabit(userId, nameText, times)
        val timesView = habit.reminders.joinToString(", ") { it.format(Keyboards.TIME_FMT) }
        sendMessage(message.chat.id, Strings.habitAdded(lang, habit.name, timesView))
    }
}
