package commands

import Lang
import Strings
import UserSettingsService
import dev.inmo.tgbotapi.extensions.api.send.sendMessage
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContext
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onCommandWithArgs
import senderLang
import senderUserId

fun BehaviourContext.registerLangCommand() {
    onCommandWithArgs("lang") { message, args ->
        val userId = message.senderUserId() ?: return@onCommandWithArgs
        val current = message.senderLang()

        if (args.isEmpty()) {
            sendMessage(message.chat.id, Strings.langCurrent(current, current))
            return@onCommandWithArgs
        }

        val chosen = Lang.parse(args.joinToString(" "))
        if (chosen == null) {
            sendMessage(message.chat.id, Strings.langInvalid(current))
            return@onCommandWithArgs
        }

        UserSettingsService.setLanguage(userId, chosen)
        sendMessage(message.chat.id, Strings.langSet(chosen))
    }
}
