package commands.addhabit

import Strings
import dev.inmo.tgbotapi.extensions.api.send.sendMessage
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContext
import dev.inmo.tgbotapi.types.IdChatIdentifier
import lang

/**
 * Timer habit: an optional integer daily target in minutes. Timers only measure elapsed
 * time, so there's no direction. Returns null (after sending a message) on cancel/invalid input.
 */
suspend fun BehaviourContext.timerFlow(chatId: IdChatIdentifier, logOnly: Boolean): HabitDraft? {
    if (logOnly) return HabitDraft()

    sendMessage(chatId, Strings.sendTimerTarget(data.lang))
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

    return HabitDraft(dailyTarget = dailyTarget)
}
