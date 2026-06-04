package commands

import services.CheckInService
import Strings
import lang
import tz
import userId
import dev.inmo.tgbotapi.extensions.api.send.sendMessage
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContext
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onCommand
import java.time.LocalDate
import java.time.ZoneOffset

fun BehaviourContext.registerStatsCommand() {
    onCommand("stats") { message ->
        val today = LocalDate.now(data.tz ?: ZoneOffset.UTC)
        val stats = CheckInService.userStats(data.userId, today)
        if (stats.isEmpty()) {
            sendMessage(message.chat.id, Strings.noStats(data.lang))
            return@onCommand
        }

        val text = buildString {
            appendLine(Strings.statsHeader(data.lang))
            stats.forEach { s ->
                appendLine("• ${s.name}")
                Strings.statsLines(data.lang, s).forEach { appendLine("    $it") }
            }
        }
        sendMessage(message.chat.id, text)
    }
}
