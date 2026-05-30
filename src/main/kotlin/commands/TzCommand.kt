package commands

import Strings
import services.UserSettingsService
import dev.inmo.tgbotapi.extensions.api.send.sendMessage
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContext
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onCommandWithArgs
import senderLang
import senderUserId
import java.time.ZoneId
import java.time.zone.ZoneRulesException

fun BehaviourContext.registerTzCommand() {
    onCommandWithArgs("tz") { message, args ->
        val userId = message.senderUserId() ?: return@onCommandWithArgs
        val lang = message.senderLang()

        if (args.isEmpty()) {
            val current = UserSettingsService.getTimezone(userId)
            val text = if (current == null) Strings.tzNotSet(lang) else Strings.tzCurrent(lang, current.id)
            sendMessage(message.chat.id, text)
            return@onCommandWithArgs
        }

        val raw = args.joinToString(" ").trim()
        val zone = try {
            ZoneId.of(raw)
        } catch (_: ZoneRulesException) {
            sendMessage(message.chat.id, Strings.tzUnknown(lang, raw))
            return@onCommandWithArgs
        } catch (_: java.time.DateTimeException) {
            sendMessage(message.chat.id, Strings.tzInvalid(lang, raw))
            return@onCommandWithArgs
        }

        UserSettingsService.setTimezone(userId, zone)
        sendMessage(message.chat.id, Strings.tzSet(lang, zone.id))
    }
}
