package commands

import HabitService
import Keyboards
import Lang
import Strings
import UserSettingsService
import dev.inmo.tgbotapi.extensions.api.answers.answerCallbackQuery
import dev.inmo.tgbotapi.extensions.api.edit.reply_markup.editMessageReplyMarkup
import dev.inmo.tgbotapi.extensions.api.send.sendMessage
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContext
import dev.inmo.tgbotapi.extensions.behaviour_builder.expectations.waitMessageDataCallbackQuery
import dev.inmo.tgbotapi.extensions.behaviour_builder.expectations.waitTextMessage
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onCommand
import dev.inmo.tgbotapi.types.MessageId
import dev.inmo.tgbotapi.types.buttons.InlineKeyboardButtons.CallbackDataInlineKeyboardButton
import dev.inmo.tgbotapi.types.buttons.InlineKeyboardMarkup
import dto.Direction
import dto.HabitType
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
        suspend fun nextText(): String =
            waitTextMessage()
                .filter { it.chat.id.chatId.long == chatLong }
                .first()
                .content
                .text
                .trim()

        suspend fun pickFromKeyboard(promptText: String, keyboard: InlineKeyboardMarkup, prefix: String): String? {
            val prompt = sendMessage(message.chat.id, promptText, replyMarkup = keyboard)
            val promptId: MessageId = prompt.messageId
            val query = waitMessageDataCallbackQuery()
                .filter { it.message.messageId == promptId && it.data.startsWith("$prefix|") }
                .first()
            runCatching { answerCallbackQuery(query) }
            runCatching { editMessageReplyMarkup(chatId = prompt.chat.id, messageId = promptId, replyMarkup = null) }
            return query.data.removePrefix("$prefix|").takeIf { it.isNotEmpty() }
        }

        sendMessage(message.chat.id, Strings.sendHabitName(lang))
        val nameText = nextText()
        if (nameText.isBlank() || nameText.startsWith("/")) {
            sendMessage(message.chat.id, Strings.cancelled(lang))
            return@onCommand
        }

        val typeChoice = pickFromKeyboard(
            Strings.pickHabitType(lang),
            typeKeyboard(lang),
            TYPE_PREFIX
        ) ?: run {
            sendMessage(message.chat.id, Strings.cancelled(lang))
            return@onCommand
        }
        val type = HabitType.entries.firstOrNull { it.value == typeChoice }
        if (type == null) {
            sendMessage(message.chat.id, Strings.cancelled(lang))
            return@onCommand
        }

        var dailyTarget: Double? = null
        var unit: String? = null
        var direction: Direction? = null

        if (type == HabitType.COUNTER) {
            sendMessage(message.chat.id, Strings.sendDailyTarget(lang))
            val raw = nextText()
            if (raw.startsWith("/")) {
                sendMessage(message.chat.id, Strings.cancelled(lang))
                return@onCommand
            }
            if (!isSkipped(raw)) {
                val n = raw.toIntOrNull()
                if (n == null || n <= 0) {
                    sendMessage(message.chat.id, Strings.invalidTarget(lang))
                    return@onCommand
                }
                dailyTarget = n.toDouble()
            }

            val dirChoice = pickFromKeyboard(
                Strings.sendDirection(lang),
                directionKeyboard(lang),
                DIR_PREFIX
            ) ?: run {
                sendMessage(message.chat.id, Strings.cancelled(lang))
                return@onCommand
            }
            direction = if (dirChoice == DIR_NONE) null
                        else Direction.entries.firstOrNull { it.value == dirChoice }
            if (direction == null && dirChoice != DIR_NONE) {
                sendMessage(message.chat.id, Strings.cancelled(lang))
                return@onCommand
            }
        }

        if (type == HabitType.QUANTITY) {
            sendMessage(message.chat.id, Strings.sendDailyTargetValue(lang))
            val raw = nextText()
            if (raw.startsWith("/")) {
                sendMessage(message.chat.id, Strings.cancelled(lang))
                return@onCommand
            }
            if (!isSkipped(raw)) {
                val v = raw.replace(',', '.').toDoubleOrNull()
                if (v == null || v <= 0.0 || v.isNaN() || v.isInfinite()) {
                    sendMessage(message.chat.id, Strings.invalidTargetValue(lang))
                    return@onCommand
                }
                dailyTarget = v
            }

            sendMessage(message.chat.id, Strings.sendUnit(lang))
            val unitRaw = nextText()
            if (unitRaw.startsWith("/")) {
                sendMessage(message.chat.id, Strings.cancelled(lang))
                return@onCommand
            }
            if (!isSkipped(unitRaw)) {
                unit = unitRaw.take(16)
            }

            val dirChoice = pickFromKeyboard(
                Strings.sendDirection(lang),
                directionKeyboard(lang),
                DIR_PREFIX
            ) ?: run {
                sendMessage(message.chat.id, Strings.cancelled(lang))
                return@onCommand
            }
            direction = if (dirChoice == DIR_NONE) null
                        else Direction.entries.firstOrNull { it.value == dirChoice }
            if (direction == null && dirChoice != DIR_NONE) {
                sendMessage(message.chat.id, Strings.cancelled(lang))
                return@onCommand
            }
        }

        val times: List<LocalTime> = if (type == HabitType.SCHEDULED) {
            sendMessage(message.chat.id, Strings.sendTimes(lang))
            val timesText = nextText()
            if (timesText.startsWith("/")) {
                sendMessage(message.chat.id, Strings.cancelled(lang))
                return@onCommand
            }
            val parsed = parseTimes(timesText)
            if (parsed == null) {
                sendMessage(message.chat.id, Strings.invalidTime(lang))
                return@onCommand
            }
            if (parsed.isEmpty()) {
                sendMessage(message.chat.id, Strings.noTimes(lang))
                return@onCommand
            }
            parsed
        } else {
            sendMessage(message.chat.id, Strings.sendOptionalTimes(lang))
            val timesText = nextText()
            if (timesText.startsWith("/")) {
                sendMessage(message.chat.id, Strings.cancelled(lang))
                return@onCommand
            }
            if (isSkipped(timesText)) {
                emptyList()
            } else {
                val parsed = parseTimes(timesText)
                if (parsed == null) {
                    sendMessage(message.chat.id, Strings.invalidTime(lang))
                    return@onCommand
                }
                parsed
            }
        }

        val habit = HabitService.addHabit(
            userId = userId,
            name = nameText,
            type = type,
            reminders = times,
            dailyTarget = dailyTarget,
            unit = unit,
            direction = direction
        )
        sendMessage(message.chat.id, Strings.habitAddedDetailed(lang, habit))
    }
}

private const val TYPE_PREFIX = "at"
private const val DIR_PREFIX = "ad"
private const val DIR_NONE = "none"

private fun typeKeyboard(lang: Lang) = InlineKeyboardMarkup(
    HabitType.entries.map { t ->
        listOf(
            CallbackDataInlineKeyboardButton(
                Strings.typeButtonLabel(lang, t),
                "$TYPE_PREFIX|${t.value}"
            )
        )
    }
)

private fun directionKeyboard(lang: Lang): InlineKeyboardMarkup {
    val rows = Direction.entries.map { d ->
        listOf(
            CallbackDataInlineKeyboardButton(
                Strings.directionButtonLabel(lang, d),
                "$DIR_PREFIX|${d.value}"
            )
        )
    } + listOf(
        listOf(
            CallbackDataInlineKeyboardButton(
                Strings.directionButtonLabel(lang, null),
                "$DIR_PREFIX|$DIR_NONE"
            )
        )
    )
    return InlineKeyboardMarkup(rows)
}

private fun isSkipped(s: String): Boolean = s.isBlank() ||
        s == "-" || s.equals("no", ignoreCase = true) || s.equals("нет", ignoreCase = true)

private fun parseTimes(text: String): List<LocalTime>? {
    val tokens = text.split(Regex("\\s+")).filter { it.isNotBlank() }
    return try {
        tokens.map { LocalTime.parse(it, Keyboards.TIME_FMT) }.distinct().sorted()
    } catch (_: DateTimeParseException) {
        null
    }
}
