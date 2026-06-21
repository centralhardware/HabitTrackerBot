package commands

import services.TrackService
import Keyboards
import Strings
import dev.inmo.tgbotapi.extensions.api.send.sendMessage
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContext
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onCommand
import dto.TrackStatus
import lang
import userId

fun BehaviourContext.registerPauseCommand() {
    onCommand("pause") { message ->
        val active = TrackService.listActive(data.userId).filter { it.status == TrackStatus.ACTIVE }
        if (active.isEmpty()) {
            sendMessage(message.chat.id, Strings.noActiveToPause(data.lang))
            return@onCommand
        }
        sendMessage(
            chatId = message.chat.id,
            text = Strings.pickTrackToPause(data.lang),
            replyMarkup = Keyboards.pickTrack("ps", active, "⏸")
        )
    }
}

fun BehaviourContext.registerResumeCommand() {
    onCommand("resume") { message ->
        val paused = TrackService.listActive(data.userId).filter { it.status == TrackStatus.PAUSED }
        if (paused.isEmpty()) {
            sendMessage(message.chat.id, Strings.noPaused(data.lang))
            return@onCommand
        }
        sendMessage(
            chatId = message.chat.id,
            text = Strings.pickTrackToResume(data.lang),
            replyMarkup = Keyboards.pickTrack("rs", paused, "▶️")
        )
    }
}
