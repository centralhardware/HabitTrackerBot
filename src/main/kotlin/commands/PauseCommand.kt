package commands

import HabitService
import Keyboards
import Strings
import dev.inmo.tgbotapi.extensions.api.send.sendMessage
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContext
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onCommand
import senderLang
import senderUserId

fun BehaviourContext.registerPauseCommand() {
    onCommand("pause") { message ->
        val userId = message.senderUserId() ?: return@onCommand
        val lang = message.senderLang()
        val active = HabitService.listActive(userId).filter { it.status == HabitService.Status.ACTIVE }
        if (active.isEmpty()) {
            sendMessage(message.chat.id, Strings.noActiveToPause(lang))
            return@onCommand
        }
        sendMessage(
            chatId = message.chat.id,
            text = Strings.pickHabitToPause(lang),
            replyMarkup = Keyboards.pickHabit("ps", active, "⏸")
        )
    }
}

fun BehaviourContext.registerResumeCommand() {
    onCommand("resume") { message ->
        val userId = message.senderUserId() ?: return@onCommand
        val lang = message.senderLang()
        val paused = HabitService.listActive(userId).filter { it.status == HabitService.Status.PAUSED }
        if (paused.isEmpty()) {
            sendMessage(message.chat.id, Strings.noPaused(lang))
            return@onCommand
        }
        sendMessage(
            chatId = message.chat.id,
            text = Strings.pickHabitToResume(lang),
            replyMarkup = Keyboards.pickHabit("rs", paused, "▶️")
        )
    }
}
