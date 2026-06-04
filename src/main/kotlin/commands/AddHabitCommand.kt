package commands

import services.HabitService
import Keyboards
import Lang
import Strings
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
import dto.ParamType
import kotlinx.coroutines.flow.first
import lang
import tz
import userId

fun BehaviourContext.registerAddHabitCommand() {
    onCommand("addhabit") { message ->
        if (data.tz == null) {
            sendMessage(message.chat.id, Strings.tzRequiredAddHabit(data.lang))
            return@onCommand
        }

        suspend fun nextText(): String =
            waitTextMessage()
                .first { it.chat.id.chatId.long == data.userId }
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
            val choice = pickFromKeyboard(Strings.sendDirection(data.lang), directionKeyboard(data.lang), DIR_PREFIX)
                ?: return DirPick.Cancelled
            val dir = if (choice == DIR_NONE) null else Direction.entries.firstOrNull { it.value == choice }
            if (dir == null && choice != DIR_NONE) return DirPick.Cancelled
            return DirPick.Picked(dir)
        }

        sendMessage(message.chat.id, Strings.sendHabitName(data.lang))
        val nameText = nextText()
        if (nameText.isBlank() || nameText.startsWith("/")) {
            sendMessage(message.chat.id, Strings.cancelled(data.lang))
            return@onCommand
        }

        val typeChoice = pickFromKeyboard(
            Strings.pickHabitType(data.lang),
            typeKeyboard(data.lang),
            TYPE_PREFIX
        ) ?: run {
            sendMessage(message.chat.id, Strings.cancelled(data.lang))
            return@onCommand
        }
        val type = HabitType.entries.firstOrNull { it.value == typeChoice }
        if (type == null) {
            sendMessage(message.chat.id, Strings.cancelled(data.lang))
            return@onCommand
        }

        // Log mode: a pure journal with no targets/streaks/trends, hidden from /stats.
        // In this mode we skip every metric-related prompt (target, direction).
        val logChoice = pickFromKeyboard(
            Strings.pickLogMode(data.lang),
            logModeKeyboard(data.lang),
            LOG_PREFIX
        ) ?: run {
            sendMessage(message.chat.id, Strings.cancelled(data.lang))
            return@onCommand
        }
        val logOnly = logChoice == LOG_ON

        var dailyTarget: Double? = null
        var unit: String? = null
        var direction: Direction? = null

        if (type == HabitType.COUNTER && !logOnly) {
            sendMessage(message.chat.id, Strings.sendDailyTarget(data.lang))
            val raw = nextText()
            if (raw.startsWith("/")) {
                sendMessage(message.chat.id, Strings.cancelled(data.lang))
                return@onCommand
            }
            if (!isSkipped(raw)) {
                val n = raw.toIntOrNull()
                if (n == null || n <= 0) {
                    sendMessage(message.chat.id, Strings.invalidTarget(data.lang))
                    return@onCommand
                }
                dailyTarget = n.toDouble()
            }

            direction = when (val d = pickDirection()) {
                is DirPick.Picked -> d.direction
                DirPick.Cancelled -> { sendMessage(message.chat.id, Strings.cancelled(data.lang)); return@onCommand }
            }
        }

        var groupFields: MutableList<HabitParam>? = null

        if (type == HabitType.QUANTITY) {
            val fields = mutableListOf<HabitParam>()
            while (true) {
                val nextLabel = if (fields.isEmpty()) Strings.sendFirstFieldName(data.lang)
                                else Strings.sendNextFieldNameOrDone(data.lang)
                sendMessage(message.chat.id, nextLabel)
                val fname = nextText()
                if (fname.startsWith("/")) {
                    sendMessage(message.chat.id, Strings.cancelled(data.lang))
                    return@onCommand
                }
                if (fields.isNotEmpty() && (fname.equals("done", ignoreCase = true) ||
                                            fname.equals("готово", ignoreCase = true) ||
                                            fname == "-")) break
                if (fname.isBlank()) {
                    sendMessage(message.chat.id, Strings.cancelled(data.lang))
                    return@onCommand
                }

                val ptypeChoice = pickFromKeyboard(
                    Strings.pickParamType(data.lang),
                    paramTypeKeyboard(data.lang),
                    PTYPE_PREFIX
                ) ?: run {
                    sendMessage(message.chat.id, Strings.cancelled(data.lang))
                    return@onCommand
                }
                val isText = ptypeChoice == PTYPE_TEXT

                if (isText) {
                    fields.add(HabitParam(id = 0, name = fname.take(64), paramType = ParamType.TEXT))
                } else {
                    var fTarget: Double? = null
                    if (!logOnly) {
                        sendMessage(message.chat.id, Strings.sendDailyTargetValue(data.lang))
                        val tRaw = nextText()
                        if (tRaw.startsWith("/")) {
                            sendMessage(message.chat.id, Strings.cancelled(data.lang))
                            return@onCommand
                        }
                        fTarget = if (isSkipped(tRaw)) null else {
                            val v = tRaw.replace(',', '.').toDoubleOrNull()
                            if (v == null || v <= 0.0 || v.isNaN() || v.isInfinite()) {
                                sendMessage(message.chat.id, Strings.invalidTargetValue(data.lang))
                                return@onCommand
                            }
                            v
                        }
                    }

                    sendMessage(message.chat.id, Strings.sendUnit(data.lang))
                    val uRaw = nextText()
                    if (uRaw.startsWith("/")) {
                        sendMessage(message.chat.id, Strings.cancelled(data.lang))
                        return@onCommand
                    }
                    val fUnit = if (isSkipped(uRaw)) null else uRaw.take(16)

                    var fDir: Direction? = null
                    if (!logOnly) {
                        fDir = when (val d = pickDirection()) {
                            is DirPick.Picked -> d.direction
                            DirPick.Cancelled -> { sendMessage(message.chat.id, Strings.cancelled(data.lang)); return@onCommand }
                        }
                    }

                    fields.add(HabitParam(id = 0, name = fname.take(64), unit = fUnit, direction = fDir, dailyTarget = fTarget, paramType = ParamType.NUMBER))
                }
            }

            if (fields.isEmpty()) {
                sendMessage(message.chat.id, Strings.cancelled(data.lang))
                return@onCommand
            }
            groupFields = fields
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
                !firstOne -> Strings.sendNextReminderTimeOrDone(data.lang)
                timesRequired -> Strings.sendFirstReminderTime(data.lang)
                else -> Strings.sendFirstReminderTimeOptional(data.lang)
            }
            sendMessage(message.chat.id, prompt)
            val timeText = nextText()
            if (timeText.startsWith("/")) {
                sendMessage(message.chat.id, Strings.cancelled(data.lang))
                return@onCommand
            }
            // "done"/"готово"/"-" finishes the list; for an optional first reminder it means "none".
            if (!firstOne && isDone(timeText)) break
            if (firstOne && !timesRequired && isSkipped(timeText)) break

            val offsetMinutes = parseTime(timeText)
            if (offsetMinutes == null) {
                sendMessage(message.chat.id, Strings.invalidTime(data.lang))
                return@onCommand
            }
            if (reminders.any { it.offsetMinutes == offsetMinutes }) {
                sendMessage(message.chat.id, Strings.duplicateTime(data.lang))
                continue
            }

            val displayTime = Strings.formatDisplayTime(offsetMinutes)
            sendMessage(message.chat.id, Strings.sendReminderDaysFor(data.lang, displayTime))
            val daysText = nextText()
            if (daysText.startsWith("/")) {
                sendMessage(message.chat.id, Strings.cancelled(data.lang))
                return@onCommand
            }
            val days = if (isSkipped(daysText)) {
                emptyList()
            } else {
                val parsed2 = parseDays(daysText)
                if (parsed2 == null) {
                    sendMessage(message.chat.id, Strings.invalidDays(data.lang))
                    return@onCommand
                }
                parsed2
            }

            reminders += HabitReminder(offsetMinutes = offsetMinutes, days = days)
        }

        // Group quantity habits carry their metadata on params[]; everything else leaves params empty
        // and the repository injects a single service param. type is already QUANTITY whenever grouped.
        val habit = HabitService.addHabit(
            Habit(
                userId = data.userId,
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
        sendMessage(message.chat.id, Strings.habitAddedDetailed(data.lang, habit))
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
private const val LOG_PREFIX = "al"
private const val LOG_ON = "log"
private const val LOG_OFF = "tracked"
private const val PTYPE_PREFIX = "apt"
private const val PTYPE_NUMBER = "number"
private const val PTYPE_TEXT = "text"
private fun paramTypeKeyboard(lang: Lang) = InlineKeyboardMarkup(
    listOf(
        listOf(CallbackDataInlineKeyboardButton(Strings.btnParamTypeNumber(lang), "$PTYPE_PREFIX|$PTYPE_NUMBER")),
        listOf(CallbackDataInlineKeyboardButton(Strings.btnParamTypeText(lang), "$PTYPE_PREFIX|$PTYPE_TEXT")),
    )
)

private fun logModeKeyboard(lang: Lang) = InlineKeyboardMarkup(
    listOf(
        listOf(CallbackDataInlineKeyboardButton(Strings.btnTracked(lang), "$LOG_PREFIX|$LOG_OFF")),
        listOf(CallbackDataInlineKeyboardButton(Strings.btnLogOnly(lang), "$LOG_PREFIX|$LOG_ON")),
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

private fun parseTime(text: String): Int? {
    val parts = text.trim().split(":")
    if (parts.size != 2) return null
    val hours = parts[0].toIntOrNull() ?: return null
    val minutes = parts[1].toIntOrNull() ?: return null
    if (hours !in 0..47 || minutes !in 0..59) return null
    return hours * 60 + minutes
}

/** Parses weekday numbers (ISO 1=Mon..7=Sun), space- or comma-separated. */
private fun parseDays(text: String): List<Int>? {
    val tokens = text.split(Regex("[\\s,]+")).filter { it.isNotBlank() }
    if (tokens.isEmpty()) return null
    val nums = tokens.map { it.toIntOrNull() ?: return null }
    if (nums.any { it !in 1..7 }) return null
    return nums.distinct().sorted()
}
