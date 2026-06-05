import dev.inmo.tgbotapi.extensions.api.answers.answerCallbackQuery
import dev.inmo.tgbotapi.extensions.api.deleteMessage
import dev.inmo.tgbotapi.extensions.api.edit.text.editMessageText
import dev.inmo.tgbotapi.extensions.api.send.sendMessage
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContext
import dev.inmo.tgbotapi.extensions.behaviour_builder.expectations.waitTextMessage
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onMessageDataCallbackQuery
import dev.inmo.tgbotapi.types.queries.callback.MessageDataCallbackQuery
import dev.inmo.tgbotapi.types.IdChatIdentifier
import dev.inmo.tgbotapi.types.MessageId
import kotlinx.coroutines.flow.first
import dto.CheckinStatus
import dto.HabitType
import services.CheckInService
import services.HabitService
import java.time.LocalDate

fun BehaviourContext.registerCallbackHandler() {
    onMessageDataCallbackQuery(Regex("^ci\\|.*")) { handleCheckIn(it) }
    onMessageDataCallbackQuery(Regex("^lg\\|.*")) { handleLog(it) }
    onMessageDataCallbackQuery(Regex("^rm\\|.*")) { handleHabitAction(it, "rm|", HabitService::softDelete, Strings::cbRemovedShort, Strings::cbRemovedFull) }
    onMessageDataCallbackQuery(Regex("^ps\\|.*")) { handlePausePick(it) }
    onMessageDataCallbackQuery(Regex("^pd\\|.*")) { handlePauseDuration(it) }
    onMessageDataCallbackQuery(Regex("^pc\\|.*")) { handlePauseCustom(it) }
    onMessageDataCallbackQuery(Regex("^rs\\|.*")) { handleHabitAction(it, "rs|", HabitService::resume, Strings::cbResumedShort, Strings::cbResumedFull) }
}

/** A habit was picked for pausing — swap the message for the duration choices. */
private suspend fun BehaviourContext.handlePausePick(query: MessageDataCallbackQuery) {
    val habitId = query.data.removePrefix("ps|").toLongOrNull() ?: run {
        answerCallbackQuery(query, text = Strings.cbError(data.lang))
        return
    }
    val msg = query.message
    runCatching {
        editMessageText(
            chatId = msg.chat.id,
            messageId = msg.messageId,
            text = Strings.pickPauseDuration(data.lang),
            replyMarkup = Keyboards.pauseDurations(habitId, data.lang),
        )
    }
    answerCallbackQuery(query)
}

/** A pause duration was chosen — apply it (`pd|<habitId>|<days>`, 0 = indefinite). */
private suspend fun BehaviourContext.handlePauseDuration(query: MessageDataCallbackQuery) {
    val parts = query.data.split("|")
    val habitId = parts.getOrNull(1)?.toLongOrNull()
    val days = parts.getOrNull(2)?.toIntOrNull()
    if (parts.size != 3 || habitId == null || days == null) {
        answerCallbackQuery(query, text = Strings.cbError(data.lang))
        return
    }
    applyPause(query.message.chat.id, query.message.messageId, habitId, days)
    answerCallbackQuery(query, text = Strings.cbPausedShort(data.lang))
}

/** "Other…" was chosen — ask for a free-text duration, then apply it (`pc|<habitId>`). */
private suspend fun BehaviourContext.handlePauseCustom(query: MessageDataCallbackQuery) {
    val habitId = query.data.removePrefix("pc|").toLongOrNull() ?: run {
        answerCallbackQuery(query, text = Strings.cbError(data.lang))
        return
    }
    answerCallbackQuery(query)
    sendMessage(query.message.chat.id, Strings.askPauseDuration(data.lang))
    val text = waitTextMessage().first { it.chat.id.chatId.long == data.userId }.content.text.trim()
    val days = parseDurationDays(text) ?: run {
        sendMessage(query.message.chat.id, Strings.cbBadDuration(data.lang))
        return
    }
    applyPause(query.message.chat.id, query.message.messageId, habitId, days)
}

/** Pauses [habitId] for [days] (>0) and replaces [messageId] with a confirmation. */
private suspend fun BehaviourContext.applyPause(
    chatId: IdChatIdentifier,
    messageId: MessageId,
    habitId: Long,
    days: Int,
) {
    if (!HabitService.pause(habitId, data.userId, days)) {
        sendMessage(chatId, Strings.cbNotFound(data.lang))
        return
    }
    val confirmation = if (days > 0) Strings.cbPausedForDays(data.lang, days) else Strings.cbPausedForever(data.lang)
    runCatching { editMessageText(chatId = chatId, messageId = messageId, text = confirmation) }
}

/**
 * Parses a free-text pause duration into whole days. Accepts a bare number ("5"), or a number
 * with a d/w/m suffix ("2w", "1m"). Returns null for anything non-positive or unrecognised.
 */
private fun parseDurationDays(input: String): Int? {
    val match = Regex("^(\\d+)\\s*([dwmDWM])?$").find(input.trim()) ?: return null
    val n = match.groupValues[1].toIntOrNull()?.takeIf { it > 0 } ?: return null
    val multiplier = when (match.groupValues[2].lowercase()) {
        "w" -> 7
        "m" -> 30
        else -> 1
    }
    return n * multiplier
}

/** Parsed `<prefix>|<id>|<date>|<action>` callback payload shared by check-in and log handlers. */
private data class ParsedCallback(val userId: Long, val lang: Lang, val id: Long, val date: LocalDate, val action: String)

/** Decodes the common callback payload, answering with an error and returning null on any malformed part. */
private suspend fun BehaviourContext.parseCallback(query: MessageDataCallbackQuery): ParsedCallback? {
    val parts = query.data.split("|")
    if (parts.size != 4) {
        answerCallbackQuery(query, text = Strings.cbBadButton(data.lang))
        return null
    }
    val id = parts[1].toLongOrNull() ?: run {
        answerCallbackQuery(query, text = Strings.cbError(data.lang))
        return null
    }
    val date = try {
        LocalDate.parse(parts[2])
    } catch (_: Exception) {
        answerCallbackQuery(query, text = Strings.cbBadDate(data.lang))
        return null
    }
    return ParsedCallback(data.userId, data.lang, id, date, parts[3])
}

private suspend fun BehaviourContext.handleCheckIn(query: MessageDataCallbackQuery) {
    val (userId, lang, reminderId, date, action) = parseCallback(query) ?: return
    val status = when (action) {
        "done" -> CheckinStatus.DONE
        "skip" -> CheckinStatus.SKIP
        else -> {
            answerCallbackQuery(query, text = Strings.cbError(lang))
            return
        }
    }

    val ok = CheckInService.record(reminderId, userId, date, status)
    if (!ok) {
        answerCallbackQuery(query, text = Strings.cbNotFound(lang))
        return
    }

    val icon = checkInIcon(status)
    val msg = query.message
    val originalText = (msg.content as? dev.inmo.tgbotapi.types.message.content.TextContent)?.text.orEmpty()
    val newText = if (originalText.isNotEmpty()) resolvedReminderText(originalText, icon) else "$icon marked"

    runCatching {
        editMessageText(
            chatId = msg.chat.id,
            messageId = msg.messageId,
            text = newText
        )
    }
    resolveCheckInMessages(reminderId, date, status, excludeMessageId = msg.messageId.long)
    answerCallbackQuery(
        query,
        text = if (status == CheckinStatus.DONE) Strings.cbDone(lang) else Strings.cbSkipped(lang)
    )
}

private suspend fun BehaviourContext.handleLog(query: MessageDataCallbackQuery) {
    val (userId, lang, habitId, date, action) = parseCallback(query) ?: return
    if (action == "del") {
        val msg = query.message
        runCatching { deleteMessage(chatId = msg.chat.id, messageId = msg.messageId) }
        answerCallbackQuery(query, text = Strings.cbDeleted(lang))
        return
    }

    // "1" logs a plain +1; "c" first asks for a free-text comment, then logs +1 with it.
    val comment = when (action) {
        "1" -> null
        "c" -> {
            answerCallbackQuery(query)
            sendMessage(query.message.chat.id, Strings.sendCounterComment(lang))
            waitTextMessage().first { it.chat.id.chatId.long == data.userId }.content.text.trim()
        }
        else -> {
            answerCallbackQuery(query, text = Strings.cbBadButton(lang))
            return
        }
    }

    if (!CheckInService.checkInCounter(habitId, userId, date, comment)) {
        if (action != "c") answerCallbackQuery(query, text = Strings.cbNotFound(lang))
        return
    }

    val habit = HabitService.findById(habitId, userId)
    val total = CheckInService.counterCountOn(habitId, date)
    val msg = query.message
    val originalText = (msg.content as? dev.inmo.tgbotapi.types.message.content.TextContent)?.text.orEmpty()
    val newText = if (habit != null && habit.type == HabitType.COUNTER) {
        Strings.counterLine(lang, habit, total, date)
    } else {
        originalText
    }

    runCatching {
        editMessageText(
            chatId = msg.chat.id,
            messageId = msg.messageId,
            text = newText,
            replyMarkup = Keyboards.logPlus(habitId, date, lang)
        )
    }
    if (action != "c") answerCallbackQuery(query, text = Strings.cbLogged(lang))
}

private suspend fun BehaviourContext.handleHabitAction(
    query: MessageDataCallbackQuery,
    prefix: String,
    action: (Long, Long) -> Boolean,
    shortText: (Lang) -> String,
    fullText: (Lang) -> String
) {
    val habitId = query.data.removePrefix(prefix).toLongOrNull() ?: run {
        answerCallbackQuery(query, text = Strings.cbError(data.lang))
        return
    }
    if (action(habitId, data.userId)) {
        val msg = query.message
        runCatching {
            editMessageText(chatId = msg.chat.id, messageId = msg.messageId, text = fullText(data.lang))
        }
        answerCallbackQuery(query, text = shortText(data.lang))
    } else {
        answerCallbackQuery(query, text = Strings.cbNotFound(data.lang))
    }
}
