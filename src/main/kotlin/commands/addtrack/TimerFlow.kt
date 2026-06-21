package commands.addtrack

import Strings
import dev.inmo.tgbotapi.extensions.api.send.sendMessage
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContext
import dev.inmo.tgbotapi.types.IdChatIdentifier
import dto.TrackParam
import dto.ParamType
import dto.TimerPhase
import lang

/**
 * Timer track: an optional integer daily target in minutes (timers only measure elapsed time, so
 * there's no direction), plus any number of extra annotation fields ("comment" params). Each extra
 * field is NUMBER or free TEXT and is filled in either before the timer starts or after it stops.
 * Returns null (after sending a message) on cancel/invalid input.
 */
suspend fun BehaviourContext.timerFlow(chatId: IdChatIdentifier, logOnly: Boolean): TrackDraft? {
    var dailyTarget: Double? = null
    if (!logOnly) {
        sendMessage(chatId, Strings.sendTimerTarget(data.lang))
        val raw = nextText()
        if (raw.startsWith("/")) {
            sendMessage(chatId, Strings.cancelled(data.lang)); return null
        }
        if (!isSkipped(raw)) {
            val n = raw.toIntOrNull()
            if (n == null || n <= 0) {
                sendMessage(chatId, Strings.invalidTarget(data.lang)); return null
            }
            // Targets are stored in seconds, but the user enters whole minutes.
            dailyTarget = n.toDouble() * 60
        }
    }

    val extras = collectTimerFields(chatId) ?: return null
    if (extras.isEmpty()) return TrackDraft(dailyTarget = dailyTarget)

    // The duration param (elapsed seconds) is the timer's primary, phase-less NUMBER param; the
    // extra annotation fields follow it. Once we supply params explicitly, the repository no longer
    // auto-creates the duration param, so we add it here.
    val durationParam = TrackParam(id = 0, paramType = ParamType.NUMBER)
    return TrackDraft(dailyTarget = dailyTarget, params = listOf(durationParam) + extras)
}

/**
 * Optional loop collecting the timer's extra annotation fields. Each field: a name, a type
 * (number/text) and a phase (before start / after stop). The loop stops when the user signals
 * "done" or skips. Returns null (after sending a message) only on cancel.
 */
private suspend fun BehaviourContext.collectTimerFields(chatId: IdChatIdentifier): List<TrackParam>? {
    val fields = mutableListOf<TrackParam>()
    while (true) {
        val prompt = if (fields.isEmpty()) Strings.sendFirstTimerFieldNameOrSkip(data.lang)
                     else Strings.sendNextTimerFieldNameOrDone(data.lang)
        sendMessage(chatId, prompt)
        val fname = nextText()
        if (fname.startsWith("/")) {
            sendMessage(chatId, Strings.cancelled(data.lang)); return null
        }
        if (isDone(fname) || (fields.isEmpty() && isSkipped(fname))) break
        if (fname.isBlank()) break

        val ptypeChoice = pickFromKeyboard(
            chatId, Strings.pickParamType(data.lang), paramTypeKeyboard(data.lang), PTYPE_PREFIX
        ) ?: run { sendMessage(chatId, Strings.cancelled(data.lang)); return null }
        val paramType = if (ptypeChoice == PTYPE_TEXT) ParamType.TEXT else ParamType.NUMBER

        val phaseChoice = pickFromKeyboard(
            chatId, Strings.pickTimerFieldPhase(data.lang), timerPhaseKeyboard(data.lang), PHASE_PREFIX
        ) ?: run { sendMessage(chatId, Strings.cancelled(data.lang)); return null }
        val phase = if (phaseChoice == PHASE_AFTER) TimerPhase.AFTER else TimerPhase.BEFORE

        fields.add(TrackParam(id = 0, name = fname.take(64), paramType = paramType, timerPhase = phase))
    }
    return fields
}
