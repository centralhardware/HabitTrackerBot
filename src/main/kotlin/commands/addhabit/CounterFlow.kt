package commands.addhabit

import Strings
import dev.inmo.tgbotapi.extensions.api.send.sendMessage
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContext
import dev.inmo.tgbotapi.types.IdChatIdentifier
import lang

/**
 * Counter habit: an optional integer daily target and an optional direction.
 * Returns null (after sending a message) if the user cancelled or sent invalid input.
 */
suspend fun BehaviourContext.counterFlow(chatId: IdChatIdentifier, logOnly: Boolean): HabitDraft? {
    if (logOnly) return HabitDraft()

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

    return HabitDraft(dailyTarget = dailyTarget, direction = direction)
}
