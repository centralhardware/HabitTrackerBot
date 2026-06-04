package commands

import Lang
import Strings
import services.UserSettingsService
import dev.inmo.tgbotapi.extensions.api.send.sendMessage
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContext
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onCommandWithArgs
import lang
import userId

fun BehaviourContext.registerLangCommand() {
    onCommandWithArgs("lang") { message, args ->
        if (args.isEmpty()) {
            sendMessage(message.chat.id, Strings.langCurrent(data.lang, data.lang))
            return@onCommandWithArgs
        }

        val chosen = Lang.parse(args.joinToString(" "))
        if (chosen == null) {
            sendMessage(message.chat.id, Strings.langInvalid(data.lang))
            return@onCommandWithArgs
        }

        UserSettingsService.setLanguageCode(data.userId, chosen.name)
        sendMessage(message.chat.id, Strings.langSet(chosen))
    }
}
