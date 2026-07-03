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
import dto.TrackType
import dto.TimerPhase
import commands.sendRecentLog
import services.CheckInService
import services.TrackService
import services.TimerService
import java.time.LocalDate

fun BehaviourContext.registerCallbackHandler() {
    onMessageDataCallbackQuery(Regex("^ci\\|.*")) { handleCheckIn(it) }
    onMessageDataCallbackQuery(Regex("^lg\\|.*")) { handleLog(it) }
    onMessageDataCallbackQuery(Regex("^rc\\|.*")) { handleRecent(it) }
    onMessageDataCallbackQuery(Regex("^tm\\|.*")) { handleTimer(it) }
    onMessageDataCallbackQuery(Regex("^rm\\|.*")) { handleTrackAction(it, "rm|", TrackService::softDelete, Strings::cbRemovedShort, Strings::cbRemovedFull) }
    onMessageDataCallbackQuery(Regex("^ps\\|.*")) { handlePausePick(it) }
    onMessageDataCallbackQuery(Regex("^pd\\|.*")) { handlePauseDuration(it) }
    onMessageDataCallbackQuery(Regex("^pc\\|.*")) { handlePauseCustom(it) }
    onMessageDataCallbackQuery(Regex("^rs\\|.*")) { handleTrackAction(it, "rs|", TrackService::resume, Strings::cbResumedShort, Strings::cbResumedFull) }
    onMessageDataCallbackQuery(Regex("^dh\\|.*")) { handleParamTrackPick(it) }
    onMessageDataCallbackQuery(Regex("^dp\\|.*")) { handleParamDelete(it) }
}

/** A track was picked for field deletion — swap the message for its deletable fields. */
private suspend fun BehaviourContext.handleParamTrackPick(query: MessageDataCallbackQuery) {
    val trackId = query.data.removePrefix("dh|").toLongOrNull() ?: run {
        answerCallbackQuery(query, text = Strings.cbError(data.lang))
        return
    }
    val track = TrackService.findById(trackId, data.userId)
    val params = track?.params?.filter { it.name != null }.orEmpty()
    if (track == null || params.isEmpty() || track.params.size <= 1) {
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
    if (TrackService.deleteParam(paramId, data.userId)) {
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

/** A track was picked for pausing — swap the message for the duration choices. */
private suspend fun BehaviourContext.handlePausePick(query: MessageDataCallbackQuery) {
    val trackId = query.data.removePrefix("ps|").toLongOrNull() ?: run {
        answerCallbackQuery(query, text = Strings.cbError(data.lang))
        return
    }
    val msg = query.message
    runCatching {
        editMessageText(
            chatId = msg.chat.id,
            messageId = msg.messageId,
            text = Strings.pickPauseDuration(data.lang),
            replyMarkup = Keyboards.pauseDurations(trackId, data.lang),
        )
    }
    answerCallbackQuery(query)
}

/** A pause duration was chosen — apply it (`pd|<trackId>|<days>`, 0 = indefinite). */
private suspend fun BehaviourContext.handlePauseDuration(query: MessageDataCallbackQuery) {
    val parts = query.data.split("|")
    val trackId = parts.getOrNull(1)?.toLongOrNull()
    val days = parts.getOrNull(2)?.toIntOrNull()
    if (parts.size != 3 || trackId == null || days == null) {
        answerCallbackQuery(query, text = Strings.cbError(data.lang))
        return
    }
    applyPause(query.message.chat.id, query.message.messageId, trackId, days)
    answerCallbackQuery(query, text = Strings.cbPausedShort(data.lang))
}

/** "Other…" was chosen — ask for a free-text duration, then apply it (`pc|<trackId>`). */
private suspend fun BehaviourContext.handlePauseCustom(query: MessageDataCallbackQuery) {
    val trackId = query.data.removePrefix("pc|").toLongOrNull() ?: run {
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
    applyPause(query.message.chat.id, query.message.messageId, trackId, days)
}

/** Pauses [trackId] for [days] (>0) and replaces [messageId] with a confirmation. */
private suspend fun BehaviourContext.applyPause(
    chatId: IdChatIdentifier,
    messageId: MessageId,
    trackId: Long,
    days: Int,
) {
    if (!TrackService.pause(trackId, data.userId, days)) {
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
    val (userId, lang, trackId, date, action) = parseCallback(query) ?: return
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

    if (!CheckInService.checkInCounter(trackId, userId, date, comment)) {
        if (action != "c") answerCallbackQuery(query, text = Strings.cbNotFound(lang))
        return
    }

    val track = TrackService.findById(trackId, userId)
    val total = CheckInService.counterCountOn(trackId, date)
    val msg = query.message
    val originalText = (msg.content as? dev.inmo.tgbotapi.types.message.content.TextContent)?.text.orEmpty()
    val newText = if (track != null && track.type == TrackType.CHECK) {
        Strings.counterLine(lang, track, total, date)
    } else {
        originalText
    }

    runCatching {
        editMessageText(
            chatId = msg.chat.id,
            messageId = msg.messageId,
            text = newText,
            replyMarkup = Keyboards.logPlus(trackId, date, lang)
        )
    }
    if (action != "c") answerCallbackQuery(query, text = Strings.cbLogged(lang))
}

/** Pages the /log recent-check-ins listing in place. Payload: `rc|<page>`. */
private suspend fun BehaviourContext.handleRecent(query: MessageDataCallbackQuery) {
    val page = query.data.removePrefix("rc|").toIntOrNull() ?: run {
        answerCallbackQuery(query, text = Strings.cbError(data.lang))
        return
    }
    // Rich messages can't be edited in place (no edit-rich API yet), so replace the old bubble with a fresh one.
    runCatching { deleteMessage(query.message.chat.id, query.message.messageId) }
    sendRecentLog(query.message.chat.id, page)
    answerCallbackQuery(query)
}

private suspend fun BehaviourContext.handleTimer(query: MessageDataCallbackQuery) {
    val (userId, lang, trackId, date, action) = parseCallback(query) ?: return
    val track = TrackService.findById(trackId, userId)
    if (track == null || track.type != TrackType.TIMER) {
        answerCallbackQuery(query, text = Strings.cbNotFound(lang))
        return
    }

    // Repaints the timer message to reflect its current running/idle (and paused) state.
    suspend fun refresh(running: Boolean) {
        val timer = TimerService.find(trackId, userId)
        val elapsed = timer?.let { TimerService.elapsedSeconds(it) } ?: 0.0
        val paused = timer?.paused == true
        val todaySeconds = CheckInService.timerSecondsOn(trackId, date)
        runCatching {
            editMessageText(
                chatId = query.message.chat.id,
                messageId = query.message.messageId,
                text = Strings.timerLine(lang, track, running, elapsed, todaySeconds, paused, timer?.beforeValues ?: emptyMap()),
                replyMarkup = Keyboards.timerControl(trackId, running, date, lang, paused)
            )
        }
    }

    val beforeFields = track.params.filter { it.timerPhase == TimerPhase.BEFORE }
    val afterFields = track.params.filter { it.timerPhase == TimerPhase.AFTER }
    val chatId = query.message.chat.id

    when (action) {
        // No "before" fields: the original one-tap start (toast only). Otherwise we must collect
        // the "before"-phase fields first and only start the timer once they're answered.
        "start" -> if (beforeFields.isEmpty()) {
            val toast = when (TimerService.start(trackId, userId)) {
                TimerService.StartOutcome.Started -> Strings.cbTimerStarted(lang)
                TimerService.StartOutcome.AlreadyRunning -> Strings.cbTimerAlreadyRunning(lang)
                TimerService.StartOutcome.NotFound -> { answerCallbackQuery(query, text = Strings.cbNotFound(lang)); return }
            }
            refresh(running = true)
            // This edited message becomes the live display the background ticker updates.
            TimerService.setMessage(trackId, userId, query.message.messageId.long)
            answerCallbackQuery(query, text = toast)
        } else {
            answerCallbackQuery(query)
            if (TimerService.find(trackId, userId) != null) {
                sendMessage(chatId, Strings.cbTimerAlreadyRunning(lang)); return
            }
            val before = collectTimerFieldValues(chatId, beforeFields) ?: run {
                sendMessage(chatId, Strings.cancelled(lang)); return
            }
            when (TimerService.start(trackId, userId, before)) {
                TimerService.StartOutcome.Started -> {
                    // The before-field Q&A pushed messages below the original timer card, so a
                    // ticker edited in place would be stranded above them. Drop the old card and
                    // post a fresh live one at the bottom so the ticking timer is the last message.
                    runCatching { deleteMessage(chatId = chatId, messageId = query.message.messageId) }
                    sendMessage(chatId, Strings.cbTimerStarted(lang))
                    val elapsed = TimerService.find(trackId, userId)?.let { TimerService.elapsedSeconds(it) } ?: 0.0
                    val todaySeconds = CheckInService.timerSecondsOn(trackId, date)
                    val live = sendMessage(
                        chatId,
                        Strings.timerLine(lang, track, running = true, elapsed, todaySeconds, beforeValues = before),
                        replyMarkup = Keyboards.timerControl(trackId, running = true, date, lang),
                    )
                    TimerService.setMessage(trackId, userId, live.messageId.long)
                }
                TimerService.StartOutcome.AlreadyRunning -> sendMessage(chatId, Strings.cbTimerAlreadyRunning(lang))
                TimerService.StartOutcome.NotFound -> sendMessage(chatId, Strings.cbNotFound(lang))
            }
        }
        "pause" -> {
            val ok = TimerService.pause(trackId, userId, date, data.tz)
            refresh(running = TimerService.find(trackId, userId) != null)
            answerCallbackQuery(query, text = if (ok) Strings.cbTimerPaused(lang) else Strings.cbTimerNotRunning(lang))
        }
        "resume" -> {
            val ok = TimerService.resume(trackId, userId)
            answerCallbackQuery(query, text = if (ok) Strings.cbTimerResumed(lang) else Strings.cbTimerNotRunning(lang))
            if (ok) {
                // Repost the card at the bottom so the ticking timer is the last message again,
                // instead of editing the old (now stranded) card in place.
                runCatching { deleteMessage(chatId = chatId, messageId = query.message.messageId) }
                val timer = TimerService.find(trackId, userId)
                val elapsed = timer?.let { TimerService.elapsedSeconds(it) } ?: 0.0
                val todaySeconds = CheckInService.timerSecondsOn(trackId, date)
                val live = sendMessage(
                    chatId,
                    Strings.timerLine(lang, track, running = true, elapsed, todaySeconds, beforeValues = timer?.beforeValues ?: emptyMap()),
                    replyMarkup = Keyboards.timerControl(trackId, running = true, date, lang),
                )
                TimerService.setMessage(trackId, userId, live.messageId.long)
            } else {
                refresh(running = TimerService.find(trackId, userId) != null)
            }
        }
        // Stop (optionally + note): stop first (so the elapsed time is frozen and safely recorded),
        // then collect the "after"-phase fields and attach them — together with the "before" values
        // stashed at start — to the just-written check-in.
        "stop", "stopc" -> {
            answerCallbackQuery(query)
            when (val o = TimerService.stop(trackId, userId, date, data.tz)) {
                is TimerService.StopOutcome.Stopped -> {
                    refresh(running = false)
                    if (action == "stopc") {
                        sendMessage(chatId, Strings.sendTimerComment(lang))
                        val note = waitTextMessage().first { it.chat.id.chatId.long == data.userId }.content.text.trim()
                        if (o.checkinId > 0) CheckInService.setComment(o.checkinId, userId, note)
                    }
                    val after = collectTimerFieldValues(chatId, afterFields) ?: emptyMap()
                    CheckInService.attachTimerFieldValues(o.checkinId, userId, trackId, o.beforeValues + after)
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
    fields: List<dto.TrackParam>,
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

private suspend fun BehaviourContext.handleTrackAction(
    query: MessageDataCallbackQuery,
    prefix: String,
    action: (Long, Long) -> Boolean,
    shortText: (Lang) -> String,
    fullText: (Lang) -> String
) {
    val trackId = query.data.removePrefix(prefix).toLongOrNull() ?: run {
        answerCallbackQuery(query, text = Strings.cbError(data.lang))
        return
    }
    if (action(trackId, data.userId)) {
        val msg = query.message
        runCatching {
            editMessageText(chatId = msg.chat.id, messageId = msg.messageId, text = fullText(data.lang))
        }
        answerCallbackQuery(query, text = shortText(data.lang))
    } else {
        answerCallbackQuery(query, text = Strings.cbNotFound(data.lang))
    }
}
