package commands

import services.TrackService
import Keyboards
import Strings
import dev.inmo.tgbotapi.extensions.api.send.sendMessage
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContext
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onCommand
import lang
import userId

/** A track field is deletable only when its row carries a name and removing it still leaves >=1 param. */
private fun hasDeletableParams(track: dto.Track) =
    track.params.size > 1 && track.params.any { it.name != null }

fun BehaviourContext.registerDeleteParamCommand() {
    onCommand("deleteparam") { message ->
        val tracks = TrackService.listActive(data.userId).filter(::hasDeletableParams)
        if (tracks.isEmpty()) {
            sendMessage(message.chat.id, Strings.noParamsToDelete(data.lang))
            return@onCommand
        }
        sendMessage(
            chatId = message.chat.id,
            text = Strings.pickTrackForParam(data.lang),
            replyMarkup = Keyboards.pickTrack("dh", tracks, "📋")
        )
    }
}
