package commands

import CheckInService
import Strings
import UserSettingsService
import dev.inmo.tgbotapi.extensions.api.send.sendMessage
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContext
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onCommand
import senderLang
import senderUserId
import java.time.LocalDate
import java.time.ZoneOffset

fun BehaviourContext.registerStatsCommand() {
    onCommand("stats") { message ->
        val userId = message.senderUserId() ?: return@onCommand
        val lang = message.senderLang()
        val tz = UserSettingsService.getTimezone(userId) ?: ZoneOffset.UTC
        val today = LocalDate.now(tz)
        val stats = CheckInService.userStats(userId, today)
        if (stats.isEmpty()) {
            sendMessage(message.chat.id, Strings.noStats(lang))
            return@onCommand
        }

        val text = buildString {
            appendLine(Strings.statsHeader(lang))
            stats.forEach { s ->
                appendLine("• ${s.name}")
                Strings.statsLines(lang, s).forEach { appendLine("    $it") }
            }
        }
        sendMessage(message.chat.id, text)
    }
}
