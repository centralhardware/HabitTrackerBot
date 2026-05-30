import dev.inmo.tgbotapi.extensions.api.answers.answerCallbackQuery
import dev.inmo.tgbotapi.extensions.api.deleteMessage
import dev.inmo.tgbotapi.extensions.api.edit.text.editMessageText
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContext
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onMessageDataCallbackQuery
import dev.inmo.tgbotapi.types.queries.callback.MessageDataCallbackQuery
import dto.CheckinStatus
import dto.HabitType
import services.CheckInService
import services.HabitService
import services.UserSettingsService
import java.time.LocalDate

fun BehaviourContext.registerCallbackHandler() {
    onMessageDataCallbackQuery(Regex("^ci\\|.*")) { handleCheckIn(it) }
    onMessageDataCallbackQuery(Regex("^lg\\|.*")) { handleLog(it) }
    onMessageDataCallbackQuery(Regex("^rm\\|.*")) { handleHabitAction(it, "rm|", HabitService::softDelete, Strings::cbRemovedShort, Strings::cbRemovedFull) }
    onMessageDataCallbackQuery(Regex("^ps\\|.*")) { handleHabitAction(it, "ps|", HabitService::pause, Strings::cbPausedShort, Strings::cbPausedFull) }
    onMessageDataCallbackQuery(Regex("^rs\\|.*")) { handleHabitAction(it, "rs|", HabitService::resume, Strings::cbResumedShort, Strings::cbResumedFull) }
}

private fun queryLang(query: MessageDataCallbackQuery): Lang {
    val userId = query.user.id.chatId.long
    val detected = Lang.of(query.user)
    UserSettingsService.touchLanguageCode(userId, detected.name)
    return Lang.stored(UserSettingsService.getLanguageCode(userId)) ?: detected
}

private suspend fun BehaviourContext.handleCheckIn(query: MessageDataCallbackQuery) {
    val userId = query.user.id.chatId.long
    val lang = queryLang(query)
    val parts = query.data.split("|")
    if (parts.size != 4) {
        answerCallbackQuery(query, text = Strings.cbBadButton(lang))
        return
    }
    val reminderId = parts[1].toLongOrNull() ?: run {
        answerCallbackQuery(query, text = Strings.cbError(lang))
        return
    }
    val date = try {
        LocalDate.parse(parts[2])
    } catch (_: Exception) {
        answerCallbackQuery(query, text = Strings.cbBadDate(lang))
        return
    }
    val status = when (parts[3]) {
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
    // Settle every other reminder message we sent for this same check-in.
    resolveCheckInMessages(reminderId, date, status, excludeMessageId = msg.messageId.long)
    answerCallbackQuery(
        query,
        text = if (status == CheckinStatus.DONE) Strings.cbDone(lang) else Strings.cbSkipped(lang)
    )
}

private suspend fun BehaviourContext.handleLog(query: MessageDataCallbackQuery) {
    val userId = query.user.id.chatId.long
    val lang = queryLang(query)
    val parts = query.data.split("|")
    if (parts.size != 4) {
        answerCallbackQuery(query, text = Strings.cbBadButton(lang))
        return
    }
    val habitId = parts[1].toLongOrNull() ?: run {
        answerCallbackQuery(query, text = Strings.cbError(lang))
        return
    }
    val date = try {
        LocalDate.parse(parts[2])
    } catch (_: Exception) {
        answerCallbackQuery(query, text = Strings.cbBadDate(lang))
        return
    }
    if (parts[3] == "del") {
        val msg = query.message
        runCatching { deleteMessage(chatId = msg.chat.id, messageId = msg.messageId) }
        answerCallbackQuery(query, text = Strings.cbDeleted(lang))
        return
    }
    if (parts[3] != "1") {
        answerCallbackQuery(query, text = Strings.cbBadButton(lang))
        return
    }

    if (!CheckInService.checkInCounter(habitId, userId, date)) {
        answerCallbackQuery(query, text = Strings.cbNotFound(lang))
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
    answerCallbackQuery(query, text = Strings.cbLogged(lang))
}

private suspend fun BehaviourContext.handleHabitAction(
    query: MessageDataCallbackQuery,
    prefix: String,
    action: (Long, Long) -> Boolean,
    shortText: (Lang) -> String,
    fullText: (Lang) -> String
) {
    val userId = query.user.id.chatId.long
    val lang = queryLang(query)
    val habitId = query.data.removePrefix(prefix).toLongOrNull() ?: run {
        answerCallbackQuery(query, text = Strings.cbError(lang))
        return
    }
    if (action(habitId, userId)) {
        val msg = query.message
        runCatching {
            editMessageText(chatId = msg.chat.id, messageId = msg.messageId, text = fullText(lang))
        }
        answerCallbackQuery(query, text = shortText(lang))
    } else {
        answerCallbackQuery(query, text = Strings.cbNotFound(lang))
    }
}
