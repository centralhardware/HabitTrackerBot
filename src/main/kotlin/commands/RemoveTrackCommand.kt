package commands

import services.TrackService
import Keyboards
import Strings
import dev.inmo.tgbotapi.extensions.api.send.sendMessage
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContext
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onCommand
import lang
import userId

fun BehaviourContext.registerRemoveTrackCommand() {
    onCommand("removetrack") { message ->
        val tracks = TrackService.listActive(data.userId)
        if (tracks.isEmpty()) {
            sendMessage(message.chat.id, Strings.nothingToRemove(data.lang))
            return@onCommand
        }
        sendMessage(
            chatId = message.chat.id,
            text = Strings.pickTrackToRemove(data.lang),
            replyMarkup = Keyboards.pickTrack("rm", tracks, "🗑")
        )
    }
}
