package commands

import Strings
import dev.inmo.tgbotapi.extensions.api.send.sendMessage
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContext
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onCommand
import senderLang

fun BehaviourContext.registerStartCommand() {
    onCommand("start") { message ->
        val lang = message.senderLang()
        sendMessage(chatId = message.chat.id, text = Strings.startHelp(lang))
    }
}
