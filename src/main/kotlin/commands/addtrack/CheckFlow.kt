package commands.addtrack

import Strings
import dev.inmo.tgbotapi.extensions.api.send.sendMessage
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContext
import dev.inmo.tgbotapi.types.IdChatIdentifier
import lang

/**
 * Check track: a done/skip track whose behavior is the product of two facts gathered here and in
 * the reminder step — whether ad-hoc check-ins are allowed (asked first; if so, an optional daily
 * target + direction follow) and whether it has a schedule (the reminders collected afterwards).
 * The orchestrator rejects a track that ends up with neither.
 * Returns null (after sending a message) if the user cancelled or sent invalid input.
 */
suspend fun BehaviourContext.checkFlow(chatId: IdChatIdentifier, logOnly: Boolean): TrackDraft? {
    val adHocChoice = pickFromKeyboard(chatId, Strings.askAllowAdHoc(data.lang), adHocKeyboard(data.lang), ADHOC_PREFIX)
        ?: run { sendMessage(chatId, Strings.cancelled(data.lang)); return null }
    val allowAdHoc = adHocChoice == ADHOC_YES

    // Targets/directions only make sense for ad-hoc counting; log-only tracks track no targets either.
    if (!allowAdHoc || logOnly) return TrackDraft(allowAdHoc = allowAdHoc)

    sendMessage(chatId, Strings.sendDailyTarget(data.lang))
    val raw = nextText()
    if (raw.startsWith("/")) {
        sendMessage(chatId, Strings.cancelled(data.lang)); return null
    }
    var dailyTarget: Double? = null
    if (!isSkipped(raw)) {
        val n = raw.toIntOrNull()
        if (n == null || n <= 0) {
            sendMessage(chatId, Strings.invalidTarget(data.lang)); return null
        }
        dailyTarget = n.toDouble()
    }

    val direction = when (val d = pickDirection(chatId)) {
        is DirPick.Picked -> d.direction
        DirPick.Cancelled -> { sendMessage(chatId, Strings.cancelled(data.lang)); return null }
    }

    return TrackDraft(dailyTarget = dailyTarget, direction = direction, allowAdHoc = true)
}
