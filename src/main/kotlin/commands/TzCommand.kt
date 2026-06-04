package commands

import Strings
import services.UserSettingsService
import dev.inmo.tgbotapi.extensions.api.send.sendMessage
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContext
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onCommandWithArgs
import lang
import tz
import userId
import java.time.ZoneId
import java.time.zone.ZoneRulesException

fun BehaviourContext.registerTzCommand() {
    onCommandWithArgs("tz") { message, args ->
        if (args.isEmpty()) {
            val text = data.tz?.let { Strings.tzCurrent(data.lang, it.id) } ?: Strings.tzNotSet(data.lang)
            sendMessage(message.chat.id, text)
            return@onCommandWithArgs
        }

        val raw = args.joinToString(" ").trim()
        val zone = try {
            ZoneId.of(raw)
        } catch (_: ZoneRulesException) {
            sendMessage(message.chat.id, Strings.tzUnknown(data.lang, raw))
            return@onCommandWithArgs
        } catch (_: java.time.DateTimeException) {
            sendMessage(message.chat.id, Strings.tzInvalid(data.lang, raw))
            return@onCommandWithArgs
        }

        UserSettingsService.setTimezone(data.userId, zone)
        sendMessage(message.chat.id, Strings.tzSet(data.lang, zone.id))
    }
}
