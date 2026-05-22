package commands

import HabitService
import Keyboards
import dev.inmo.tgbotapi.extensions.api.send.sendMessage
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContext
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onCommand
import senderUserId

fun BehaviourContext.registerPauseCommand() {
    onCommand("pause") { message ->
        val userId = message.senderUserId() ?: return@onCommand
        val active = HabitService.listActive(userId).filter { it.pausedAt == null }
        if (active.isEmpty()) {
            sendMessage(message.chat.id, "No active habits to pause.")
            return@onCommand
        }
        sendMessage(
            chatId = message.chat.id,
            text = "Pick a habit to pause:",
            replyMarkup = Keyboards.pickHabit("ps", active, "⏸")
        )
    }
}

fun BehaviourContext.registerResumeCommand() {
    onCommand("resume") { message ->
        val userId = message.senderUserId() ?: return@onCommand
        val paused = HabitService.listActive(userId).filter { it.pausedAt != null }
        if (paused.isEmpty()) {
            sendMessage(message.chat.id, "No paused habits.")
            return@onCommand
        }
        sendMessage(
            chatId = message.chat.id,
            text = "Pick a habit to resume:",
            replyMarkup = Keyboards.pickHabit("rs", paused, "▶️")
        )
    }
}
