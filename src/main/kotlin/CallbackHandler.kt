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
import dto.TimerPhase
import services.CheckInService
import services.HabitService
import services.TimerService
import java.time.LocalDate

fun BehaviourContext.registerCallbackHandler() {
    onMessageDataCallbackQuery(Regex("^ci\\|.*")) { handleCheckIn(it) }
    onMessageDataCallbackQuery(Regex("^lg\\|.*")) { handleLog(it) }
    onMessageDataCallbackQuery(Regex("^tm\\|.*")) { handleTimer(it) }
    onMessageDataCallbackQuery(Regex("^rm\\|.*")) { handleHabitAction(it, "rm|", HabitService::softDelete, Strings::cbRemovedShort, Strings::cbRemovedFull) }
    onMessageDataCallbackQuery(Regex("^ps\\|.*")) { handlePausePick(it) }
    onMessageDataCallbackQuery(Regex("^pd\\|.*")) { handlePauseDuration(it) }
    onMessageDataCallbackQuery(Regex("^pc\\|.*")) { handlePauseCustom(it) }
    onMessageDataCallbackQuery(Regex("^rs\\|.*")) { handleHabitAction(it, "rs|", HabitService::resume, Strings::cbResumedShort, Strings::cbResumedFull) }
    onMessageDataCallbackQuery(Regex("^dh\\|.*")) { handleParamHabitPick(it) }
    onMessageDataCallbackQuery(Regex("^dp\\|.*")) { handleParamDelete(it) }
}

/** A habit was picked for field deletion — swap the message for its deletable fields. */
private suspend fun BehaviourContext.handleParamHabitPick(query: MessageDataCallbackQuery) {
    val habitId = query.data.removePrefix("dh|").toLongOrNull() ?: run {
        answerCallbackQuery(query, text = Strings.cbError(data.lang))
        return
    }
    val habit = HabitService.findById(habitId, data.userId)
    val params = habit?.params?.filter { it.name != null }.orEmpty()
    if (habit == null || params.isEmpty() || habit.params.size <= 1) {
        answerCallbackQuery(query, text = Strings.cbNotFound(data.lang))
        return
    }
    runCatching {
        editMessageText(
            chatId = query.message.chat.id,
            messageId = query.message.messageId,
            text = Strings.pickParamToDelete(data.lang),
            replyMarkup = Keyboards.pickParam(params, data.lang),
        )
    }
    answerCallbackQuery(query)
}

/** A field was picked — delete it (`dp|<paramId>`) and confirm in place. */
private suspend fun BehaviourContext.handleParamDelete(query: MessageDataCallbackQuery) {
    val paramId = query.data.removePrefix("dp|").toLongOrNull() ?: run {
        answerCallbackQuery(query, text = Strings.cbError(data.lang))
        return
    }
    if (HabitService.deleteParam(paramId, data.userId)) {
        runCatching {
            editMessageText(
                chatId = query.message.chat.id,
                messageId = query.message.messageId,
                text = Strings.cbParamDeleted(data.lang),
            )
        }
        answerCallbackQuery(query, text = Strings.cbDeleted(data.lang))
    } else {
        answerCallbackQuery(query, text = Strings.cbNotFound(data.lang))
    }
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
    val newText = if (habit != null && habit.type == HabitType.CHECK) {
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

private suspend fun BehaviourContext.handleTimer(query: MessageDataCallbackQuery) {
    val (userId, lang, habitId, date, action) = parseCallback(query) ?: return
    val habit = HabitService.findById(habitId, userId)
    if (habit == null || habit.type != HabitType.TIMER) {
        answerCallbackQuery(query, text = Strings.cbNotFound(lang))
        return
    }

    // Repaints the timer message to reflect its current running/idle (and paused) state.
    suspend fun refresh(running: Boolean) {
        val timer = TimerService.find(habitId, userId)
        val elapsed = timer?.let { TimerService.elapsedSeconds(it) } ?: 0.0
        val paused = timer?.paused == true
        val todaySeconds = CheckInService.timerSecondsOn(habitId, date)
        runCatching {
            editMessageText(
                chatId = query.message.chat.id,
                messageId = query.message.messageId,
                text = Strings.timerLine(lang, habit, running, elapsed, todaySeconds, paused),
                replyMarkup = Keyboards.timerControl(habitId, running, date, lang, paused)
            )
        }
    }

    val beforeFields = habit.params.filter { it.timerPhase == TimerPhase.BEFORE }
    val afterFields = habit.params.filter { it.timerPhase == TimerPhase.AFTER }
    val chatId = query.message.chat.id

    when (action) {
        // No "before" fields: the original one-tap start (toast only). Otherwise we must collect
        // the "before"-phase fields first and only start the timer once they're answered.
        "start" -> if (beforeFields.isEmpty()) {
            val toast = when (TimerService.start(habitId, userId)) {
                TimerService.StartOutcome.Started -> Strings.cbTimerStarted(lang)
                TimerService.StartOutcome.AlreadyRunning -> Strings.cbTimerAlreadyRunning(lang)
                TimerService.StartOutcome.NotFound -> { answerCallbackQuery(query, text = Strings.cbNotFound(lang)); return }
            }
            refresh(running = true)
            // This edited message becomes the live display the background ticker updates.
            TimerService.setMessage(habitId, userId, query.message.messageId.long)
            answerCallbackQuery(query, text = toast)
        } else {
            answerCallbackQuery(query)
            if (TimerService.find(habitId, userId) != null) {
                sendMessage(chatId, Strings.cbTimerAlreadyRunning(lang)); return
            }
            val before = collectTimerFieldValues(chatId, beforeFields) ?: run {
                sendMessage(chatId, Strings.cancelled(lang)); return
            }
            when (TimerService.start(habitId, userId, before)) {
                TimerService.StartOutcome.Started -> {
                    // The before-field Q&A pushed messages below the original timer card, so a
                    // ticker edited in place would be stranded above them. Drop the old card and
                    // post a fresh live one at the bottom so the ticking timer is the last message.
                    runCatching { deleteMessage(chatId = chatId, messageId = query.message.messageId) }
                    sendMessage(chatId, Strings.cbTimerStarted(lang))
                    val elapsed = TimerService.find(habitId, userId)?.let { TimerService.elapsedSeconds(it) } ?: 0.0
                    val todaySeconds = CheckInService.timerSecondsOn(habitId, date)
                    val live = sendMessage(
                        chatId,
                        Strings.timerLine(lang, habit, running = true, elapsed, todaySeconds),
                        replyMarkup = Keyboards.timerControl(habitId, running = true, date, lang),
                    )
                    TimerService.setMessage(habitId, userId, live.messageId.long)
                }
                TimerService.StartOutcome.AlreadyRunning -> sendMessage(chatId, Strings.cbTimerAlreadyRunning(lang))
                TimerService.StartOutcome.NotFound -> sendMessage(chatId, Strings.cbNotFound(lang))
            }
        }
        "pause" -> {
            val ok = TimerService.pause(habitId, userId, date, data.tz)
            refresh(running = TimerService.find(habitId, userId) != null)
            answerCallbackQuery(query, text = if (ok) Strings.cbTimerPaused(lang) else Strings.cbTimerNotRunning(lang))
        }
        "resume" -> {
            val ok = TimerService.resume(habitId, userId)
            refresh(running = TimerService.find(habitId, userId) != null)
            answerCallbackQuery(query, text = if (ok) Strings.cbTimerResumed(lang) else Strings.cbTimerNotRunning(lang))
        }
        // Stop (optionally + note): stop first (so the elapsed time is frozen and safely recorded),
        // then collect the "after"-phase fields and attach them — together with the "before" values
        // stashed at start — to the just-written check-in.
        "stop", "stopc" -> {
            answerCallbackQuery(query)
            when (val o = TimerService.stop(habitId, userId, date, data.tz)) {
                is TimerService.StopOutcome.Stopped -> {
                    refresh(running = false)
                    if (action == "stopc") {
                        sendMessage(chatId, Strings.sendTimerComment(lang))
                        val note = waitTextMessage().first { it.chat.id.chatId.long == data.userId }.content.text.trim()
                        if (o.checkinId > 0) CheckInService.setComment(o.checkinId, userId, note)
                    }
                    val after = collectTimerFieldValues(chatId, afterFields) ?: emptyMap()
                    CheckInService.attachTimerFieldValues(o.checkinId, userId, habitId, o.beforeValues + after)
                    sendMessage(chatId, Strings.cbTimerStopped(lang, o.seconds))
                }
                TimerService.StopOutcome.NotRunning -> { refresh(running = false); sendMessage(chatId, Strings.cbTimerNotRunning(lang)) }
                TimerService.StopOutcome.NotFound -> sendMessage(chatId, Strings.cbNotFound(lang))
            }
        }
        else -> answerCallbackQuery(query, text = Strings.cbBadButton(lang))
    }
}

/**
 * Prompts for each [fields] value one at a time (a timer's before/after annotation fields),
 * returning paramId → entered text. A field answered with "-" is skipped; a "/command" aborts the
 * whole collection (returns null). Returns an empty map when there are no fields.
 */
private suspend fun BehaviourContext.collectTimerFieldValues(
    chatId: IdChatIdentifier,
    fields: List<dto.HabitParam>,
): Map<Long, String>? {
    if (fields.isEmpty()) return emptyMap()
    val result = LinkedHashMap<Long, String>()
    for (f in fields) {
        sendMessage(chatId, Strings.sendTimerFieldValue(data.lang, f.name ?: ""))
        // NUMBER fields re-prompt until a parseable number (or "-"/skip) is sent, so a number
        // field can never be saved with non-numeric text. TEXT fields take whatever is typed.
        while (true) {
            val text = waitTextMessage().first { it.chat.id.chatId.long == data.userId }.content.text.trim()
            if (text.startsWith("/")) return null
            if (text == "-") break
            if (f.paramType == dto.ParamType.NUMBER) {
                val n = text.replace(',', '.').toDoubleOrNull()
                if (n == null || n.isNaN() || n.isInfinite()) {
                    sendMessage(chatId, Strings.timerFieldNotANumber(data.lang, f.name ?: ""))
                    continue
                }
                // Store the canonical numeric form so later toDoubleOrNull reads back cleanly.
                result[f.id] = n.toString()
            } else {
                result[f.id] = text
            }
            break
        }
    }
    return result
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
