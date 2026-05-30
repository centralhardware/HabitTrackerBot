package commands

import services.HabitService
import Keyboards
import Lang
import Strings
import services.UserSettingsService
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
import dto.Habit
import dto.HabitParam
import dto.HabitReminder
import dto.HabitType
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
                .first { it.chat.id.chatId.long == chatLong }
                .content
                .text
                .trim()

        suspend fun pickFromKeyboard(promptText: String, keyboard: InlineKeyboardMarkup, prefix: String): String? {
            val prompt = sendMessage(message.chat.id, promptText, replyMarkup = keyboard)
            val promptId: MessageId = prompt.messageId
            val query = waitMessageDataCallbackQuery()
                .first { it.message.messageId == promptId && it.data.startsWith("$prefix|") }
            runCatching { answerCallbackQuery(query) }
            runCatching { editMessageReplyMarkup(chatId = prompt.chat.id, messageId = promptId, replyMarkup = null) }
            return query.data.removePrefix("$prefix|").takeIf { it.isNotEmpty() }
        }

        // Direction prompt is asked from three places (counter, single quantity, grouped field);
        // keep the keyboard + parse + validate in one spot. Returns null when the user cancelled.
        suspend fun pickDirection(): DirPick {
            val choice = pickFromKeyboard(Strings.sendDirection(lang), directionKeyboard(lang), DIR_PREFIX)
                ?: return DirPick.Cancelled
            val dir = if (choice == DIR_NONE) null else Direction.entries.firstOrNull { it.value == choice }
            if (dir == null && choice != DIR_NONE) return DirPick.Cancelled
            return DirPick.Picked(dir)
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

        // Log mode: a pure journal with no targets/streaks/trends, hidden from /stats.
        // In this mode we skip every metric-related prompt (target, direction).
        val logChoice = pickFromKeyboard(
            Strings.pickLogMode(lang),
            logModeKeyboard(lang),
            LOG_PREFIX
        ) ?: run {
            sendMessage(message.chat.id, Strings.cancelled(lang))
            return@onCommand
        }
        val logOnly = logChoice == LOG_ON

        var dailyTarget: Double? = null
        var unit: String? = null
        var direction: Direction? = null

        if (type == HabitType.COUNTER && !logOnly) {
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

            direction = when (val d = pickDirection()) {
                is DirPick.Picked -> d.direction
                DirPick.Cancelled -> { sendMessage(message.chat.id, Strings.cancelled(lang)); return@onCommand }
            }
        }

        var groupFields: MutableList<HabitParam>? = null

        if (type == HabitType.QUANTITY) {
            val modeChoice = pickFromKeyboard(
                Strings.pickQuantityMode(lang),
                quantityModeKeyboard(lang),
                MODE_PREFIX
            ) ?: run {
                sendMessage(message.chat.id, Strings.cancelled(lang))
                return@onCommand
            }

            if (modeChoice == MODE_GROUP) {
                groupFields = mutableListOf()
                while (true) {
                    val nextLabel = if (groupFields.isEmpty()) Strings.sendFirstFieldName(lang)
                                    else Strings.sendNextFieldNameOrDone(lang)
                    sendMessage(message.chat.id, nextLabel)
                    val fname = nextText()
                    if (fname.startsWith("/")) {
                        sendMessage(message.chat.id, Strings.cancelled(lang))
                        return@onCommand
                    }
                    if (groupFields.isNotEmpty() && (fname.equals("done", ignoreCase = true) ||
                                                     fname.equals("готово", ignoreCase = true) ||
                                                     fname == "-")) break
                    if (fname.isBlank()) {
                        sendMessage(message.chat.id, Strings.cancelled(lang))
                        return@onCommand
                    }

                    var fTarget: Double? = null
                    if (!logOnly) {
                        sendMessage(message.chat.id, Strings.sendDailyTargetValue(lang))
                        val tRaw = nextText()
                        if (tRaw.startsWith("/")) {
                            sendMessage(message.chat.id, Strings.cancelled(lang))
                            return@onCommand
                        }
                        fTarget = if (isSkipped(tRaw)) null else {
                            val v = tRaw.replace(',', '.').toDoubleOrNull()
                            if (v == null || v <= 0.0 || v.isNaN() || v.isInfinite()) {
                                sendMessage(message.chat.id, Strings.invalidTargetValue(lang))
                                return@onCommand
                            }
                            v
                        }
                    }

                    sendMessage(message.chat.id, Strings.sendUnit(lang))
                    val uRaw = nextText()
                    if (uRaw.startsWith("/")) {
                        sendMessage(message.chat.id, Strings.cancelled(lang))
                        return@onCommand
                    }
                    val fUnit = if (isSkipped(uRaw)) null else uRaw.take(16)

                    var fDir: Direction? = null
                    if (!logOnly) {
                        fDir = when (val d = pickDirection()) {
                            is DirPick.Picked -> d.direction
                            DirPick.Cancelled -> { sendMessage(message.chat.id, Strings.cancelled(lang)); return@onCommand }
                        }
                    }

                    groupFields.add(HabitParam(id = 0, name = fname.take(64), unit = fUnit, direction = fDir, dailyTarget = fTarget))
                }

                if (groupFields.isEmpty()) {
                    sendMessage(message.chat.id, Strings.cancelled(lang))
                    return@onCommand
                }
            } else {
                if (!logOnly) {
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

                if (!logOnly) {
                    direction = when (val d = pickDirection()) {
                        is DirPick.Picked -> d.direction
                        DirPick.Cancelled -> { sendMessage(message.chat.id, Strings.cancelled(lang)); return@onCommand }
                    }
                }
            }
        }

        // Collect reminders one at a time: each reminder gets its own time and its own
        // arbitrary weekdays. Scheduled habits need at least one; for other types reminders
        // are optional. Asking time then days per reminder lets the same habit fire on
        // different days at different times.
        val timesRequired = type == HabitType.SCHEDULED
        val reminders = mutableListOf<HabitReminder>()
        while (true) {
            val firstOne = reminders.isEmpty()
            val prompt = when {
                !firstOne -> Strings.sendNextReminderTimeOrDone(lang)
                timesRequired -> Strings.sendFirstReminderTime(lang)
                else -> Strings.sendFirstReminderTimeOptional(lang)
            }
            sendMessage(message.chat.id, prompt)
            val timeText = nextText()
            if (timeText.startsWith("/")) {
                sendMessage(message.chat.id, Strings.cancelled(lang))
                return@onCommand
            }
            // "done"/"готово"/"-" finishes the list; for an optional first reminder it means "none".
            if (!firstOne && isDone(timeText)) break
            if (firstOne && !timesRequired && isSkipped(timeText)) break

            val time = parseTime(timeText)
            if (time == null) {
                sendMessage(message.chat.id, Strings.invalidTime(lang))
                return@onCommand
            }
            if (reminders.any { it.time == time }) {
                sendMessage(message.chat.id, Strings.duplicateTime(lang))
                continue
            }

            sendMessage(message.chat.id, Strings.sendReminderDaysFor(lang, time.format(Keyboards.TIME_FMT)))
            val daysText = nextText()
            if (daysText.startsWith("/")) {
                sendMessage(message.chat.id, Strings.cancelled(lang))
                return@onCommand
            }
            val days = if (isSkipped(daysText)) {
                emptyList()
            } else {
                val parsed = parseDays(daysText)
                if (parsed == null) {
                    sendMessage(message.chat.id, Strings.invalidDays(lang))
                    return@onCommand
                }
                parsed
            }
            reminders += HabitReminder(time = time, days = days)
        }

        // Group quantity habits carry their metadata on params[]; everything else leaves params empty
        // and the repository injects a single service param. type is already QUANTITY whenever grouped.
        val habit = HabitService.addHabit(
            Habit(
                userId = userId,
                name = nameText,
                type = type,
                dailyTarget = dailyTarget,
                unit = unit,
                direction = direction,
                reminders = reminders,
                params = groupFields ?: emptyList(),
                logOnly = logOnly,
            )
        )
        sendMessage(message.chat.id, Strings.habitAddedDetailed(lang, habit))
    }
}

/** Outcome of a direction prompt: a chosen direction (possibly null = "no direction") or a cancel. */
private sealed interface DirPick {
    data class Picked(val direction: Direction?) : DirPick
    data object Cancelled : DirPick
}

private const val TYPE_PREFIX = "at"
private const val DIR_PREFIX = "ad"
private const val DIR_NONE = "none"
private const val MODE_PREFIX = "am"
private const val MODE_SINGLE = "single"
private const val MODE_GROUP = "group"
private const val LOG_PREFIX = "al"
private const val LOG_ON = "log"
private const val LOG_OFF = "tracked"

private fun logModeKeyboard(lang: Lang) = InlineKeyboardMarkup(
    listOf(
        listOf(CallbackDataInlineKeyboardButton(Strings.btnTracked(lang), "$LOG_PREFIX|$LOG_OFF")),
        listOf(CallbackDataInlineKeyboardButton(Strings.btnLogOnly(lang), "$LOG_PREFIX|$LOG_ON")),
    )
)

private fun quantityModeKeyboard(lang: Lang) = InlineKeyboardMarkup(
    listOf(
        listOf(CallbackDataInlineKeyboardButton(Strings.btnModeSingle(lang), "$MODE_PREFIX|$MODE_SINGLE")),
        listOf(CallbackDataInlineKeyboardButton(Strings.btnModeGroup(lang), "$MODE_PREFIX|$MODE_GROUP")),
    )
)

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

private fun isDone(s: String): Boolean = s == "-" ||
        s.equals("done", ignoreCase = true) || s.equals("готово", ignoreCase = true)

private fun parseTime(text: String): LocalTime? = try {
    LocalTime.parse(text.trim(), Keyboards.TIME_FMT)
} catch (_: DateTimeParseException) {
    null
}

/** Parses weekday numbers (ISO 1=Mon..7=Sun), space- or comma-separated. */
private fun parseDays(text: String): List<Int>? {
    val tokens = text.split(Regex("[\\s,]+")).filter { it.isNotBlank() }
    if (tokens.isEmpty()) return null
    val nums = tokens.map { it.toIntOrNull() ?: return null }
    if (nums.any { it !in 1..7 }) return null
    return nums.distinct().sorted()
}
