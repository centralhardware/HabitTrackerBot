package commands

import UserSettingsService
import dev.inmo.tgbotapi.extensions.api.send.sendMessage
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContext
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onCommandWithArgs
import senderUserId
import java.time.ZoneId
import java.time.zone.ZoneRulesException

fun BehaviourContext.registerTzCommand() {
    onCommandWithArgs("tz") { message, args ->
        val userId = message.senderUserId() ?: return@onCommandWithArgs

        if (args.isEmpty()) {
            val current = UserSettingsService.getTimezone(userId)
            val text = if (current == null) {
                "Timezone is not set. Set it with /tz <IANA name>, e.g. /tz Europe/Moscow"
            } else {
                "Your timezone: ${current.id}\nChange with /tz <IANA name>"
            }
            sendMessage(message.chat.id, text)
            return@onCommandWithArgs
        }

        val raw = args.joinToString(" ").trim()
        val zone = try {
            ZoneId.of(raw)
        } catch (_: ZoneRulesException) {
            sendMessage(message.chat.id, "Unknown timezone: $raw. Use IANA names like Europe/Moscow.")
            return@onCommandWithArgs
        } catch (_: java.time.DateTimeException) {
            sendMessage(message.chat.id, "Invalid timezone: $raw.")
            return@onCommandWithArgs
        }

        UserSettingsService.setTimezone(userId, zone)
        sendMessage(message.chat.id, "Timezone set to ${zone.id}.")
    }
}
