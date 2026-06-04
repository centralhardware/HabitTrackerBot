package commands

import services.HabitService
import Keyboards
import Strings
import dev.inmo.tgbotapi.extensions.api.send.sendMessage
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContext
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onCommand
import dto.HabitStatus
import lang
import userId

fun BehaviourContext.registerPauseCommand() {
    onCommand("pause") { message ->
        val active = HabitService.listActive(data.userId).filter { it.status == HabitStatus.ACTIVE }
        if (active.isEmpty()) {
            sendMessage(message.chat.id, Strings.noActiveToPause(data.lang))
            return@onCommand
        }
        sendMessage(
            chatId = message.chat.id,
            text = Strings.pickHabitToPause(data.lang),
            replyMarkup = Keyboards.pickHabit("ps", active, "⏸")
        )
    }
}

fun BehaviourContext.registerResumeCommand() {
    onCommand("resume") { message ->
        val paused = HabitService.listActive(data.userId).filter { it.status == HabitStatus.PAUSED }
        if (paused.isEmpty()) {
            sendMessage(message.chat.id, Strings.noPaused(data.lang))
            return@onCommand
        }
        sendMessage(
            chatId = message.chat.id,
            text = Strings.pickHabitToResume(data.lang),
            replyMarkup = Keyboards.pickHabit("rs", paused, "▶️")
        )
    }
}
