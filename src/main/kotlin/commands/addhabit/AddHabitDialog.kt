package commands.addhabit

import Lang
import Strings
import dev.inmo.tgbotapi.extensions.api.answers.answerCallbackQuery
import dev.inmo.tgbotapi.extensions.api.edit.reply_markup.editMessageReplyMarkup
import dev.inmo.tgbotapi.extensions.api.send.sendMessage
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContext
import dev.inmo.tgbotapi.extensions.behaviour_builder.expectations.waitMessageDataCallbackQuery
import dev.inmo.tgbotapi.extensions.behaviour_builder.expectations.waitTextMessage
import dev.inmo.tgbotapi.types.IdChatIdentifier
import dev.inmo.tgbotapi.types.MessageId
import dev.inmo.tgbotapi.types.buttons.InlineKeyboardButtons.CallbackDataInlineKeyboardButton
import dev.inmo.tgbotapi.types.buttons.InlineKeyboardMarkup
import dto.Direction
import dto.HabitParam
import dto.HabitType
import kotlinx.coroutines.flow.first
import lang
import userId

/**
 * Type-specific fields gathered by an /addhabit flow. The orchestrator combines this with the
 * habit name, type, log mode and reminders to build the final [dto.Habit].
 */
data class HabitDraft(
    val dailyTarget: Double? = null,
    val unit: String? = null,
    val direction: Direction? = null,
    val params: List<HabitParam> = emptyList(),
)

/** Outcome of a direction prompt: a chosen direction (possibly null = "no direction") or a cancel. */
sealed interface DirPick {
    data class Picked(val direction: Direction?) : DirPick
    data object Cancelled : DirPick
}

// ---- shared dialog primitives ----

/** Waits for the next plain-text message from the dialog's owner and returns its trimmed text. */
suspend fun BehaviourContext.nextText(): String =
    waitTextMessage()
        .first { it.chat.id.chatId.long == data.userId }
        .content
        .text
        .trim()

/**
 * Sends [promptText] with [keyboard] and waits for a callback whose data starts with `"$prefix|"`,
 * returning the part after the prefix (or null if it was empty). Clears the keyboard afterward.
 */
suspend fun BehaviourContext.pickFromKeyboard(
    chatId: IdChatIdentifier,
    promptText: String,
    keyboard: InlineKeyboardMarkup,
    prefix: String,
): String? {
    val prompt = sendMessage(chatId, promptText, replyMarkup = keyboard)
    val promptId: MessageId = prompt.messageId
    val query = waitMessageDataCallbackQuery()
        .first { it.message.messageId == promptId && it.data.startsWith("$prefix|") }
    runCatching { answerCallbackQuery(query) }
    runCatching { editMessageReplyMarkup(chatId = prompt.chat.id, messageId = promptId, replyMarkup = null) }
    return query.data.removePrefix("$prefix|").takeIf { it.isNotEmpty() }
}

suspend fun BehaviourContext.pickDirection(chatId: IdChatIdentifier): DirPick {
    val choice = pickFromKeyboard(chatId, Strings.sendDirection(data.lang), directionKeyboard(data.lang), DIR_PREFIX)
        ?: return DirPick.Cancelled
    val dir = if (choice == DIR_NONE) null else Direction.entries.firstOrNull { it.value == choice }
    if (dir == null && choice != DIR_NONE) return DirPick.Cancelled
    return DirPick.Picked(dir)
}

// ---- callback prefixes / sentinel values ----

internal const val TYPE_PREFIX = "at"
internal const val DIR_PREFIX = "ad"
internal const val DIR_NONE = "none"
internal const val LOG_PREFIX = "al"
internal const val LOG_ON = "log"
internal const val LOG_OFF = "tracked"
internal const val PTYPE_PREFIX = "apt"
internal const val PTYPE_NUMBER = "number"
internal const val PTYPE_TEXT = "text"
internal const val PHASE_PREFIX = "aph"
internal const val PHASE_BEFORE = "before"
internal const val PHASE_AFTER = "after"

// ---- keyboards ----

internal fun paramTypeKeyboard(lang: Lang) = InlineKeyboardMarkup(
    listOf(
        listOf(CallbackDataInlineKeyboardButton(Strings.btnParamTypeNumber(lang), "$PTYPE_PREFIX|$PTYPE_NUMBER")),
        listOf(CallbackDataInlineKeyboardButton(Strings.btnParamTypeText(lang), "$PTYPE_PREFIX|$PTYPE_TEXT")),
    )
)

internal fun timerPhaseKeyboard(lang: Lang) = InlineKeyboardMarkup(
    listOf(
        listOf(CallbackDataInlineKeyboardButton(Strings.btnPhaseBefore(lang), "$PHASE_PREFIX|$PHASE_BEFORE")),
        listOf(CallbackDataInlineKeyboardButton(Strings.btnPhaseAfter(lang), "$PHASE_PREFIX|$PHASE_AFTER")),
    )
)

internal fun logModeKeyboard(lang: Lang) = InlineKeyboardMarkup(
    listOf(
        listOf(CallbackDataInlineKeyboardButton(Strings.btnTracked(lang), "$LOG_PREFIX|$LOG_OFF")),
        listOf(CallbackDataInlineKeyboardButton(Strings.btnLogOnly(lang), "$LOG_PREFIX|$LOG_ON")),
    )
)

internal fun typeKeyboard(lang: Lang) = InlineKeyboardMarkup(
    HabitType.entries.map { t ->
        listOf(
            CallbackDataInlineKeyboardButton(
                Strings.typeButtonLabel(lang, t),
                "$TYPE_PREFIX|${t.value}"
            )
        )
    }
)

internal fun directionKeyboard(lang: Lang): InlineKeyboardMarkup {
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

// ---- text parsing ----

internal fun isSkipped(s: String): Boolean = s.isBlank() ||
        s == "-" || s.equals("no", ignoreCase = true) || s.equals("нет", ignoreCase = true)

internal fun isDone(s: String): Boolean = s == "-" ||
        s.equals("done", ignoreCase = true) || s.equals("готово", ignoreCase = true)

internal fun parseTime(text: String): Int? {
    val parts = text.trim().split(":")
    if (parts.size != 2) return null
    val hours = parts[0].toIntOrNull() ?: return null
    val minutes = parts[1].toIntOrNull() ?: return null
    if (hours !in 0..47 || minutes !in 0..59) return null
    return hours * 60 + minutes
}

/** Parses weekday numbers (ISO 1=Mon..7=Sun), space- or comma-separated. */
internal fun parseDays(text: String): List<Int>? {
    val tokens = text.split(Regex("[\\s,]+")).filter { it.isNotBlank() }
    if (tokens.isEmpty()) return null
    val nums = tokens.map { it.toIntOrNull() ?: return null }
    if (nums.any { it !in 1..7 }) return null
    return nums.distinct().sorted()
}
