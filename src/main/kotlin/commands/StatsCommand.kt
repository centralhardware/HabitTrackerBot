package commands

import CheckInService
import Strings
import dev.inmo.tgbotapi.extensions.api.send.sendMessage
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContext
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onCommand
import senderLang
import senderUserId

fun BehaviourContext.registerStatsCommand() {
    onCommand("stats") { message ->
        val userId = message.senderUserId() ?: return@onCommand
        val lang = message.senderLang()
        val stats = CheckInService.userStats(userId)
        if (stats.isEmpty()) {
            sendMessage(message.chat.id, Strings.noStats(lang))
            return@onCommand
        }

        val text = buildString {
            appendLine(Strings.statsHeader(lang))
            stats.forEach { s ->
                val total = s.doneCount + s.skipCount
                val rate = if (total > 0) "%.0f%%".format(s.doneCount * 100.0 / total) else "—"
                appendLine("• ${s.name}")
                appendLine("    ✅ ${s.doneCount}   ❌ ${s.skipCount}   ${Strings.statsCompletion(lang)}: $rate")
                appendLine("    ${Strings.statsStreak(lang, s.streak)}")
            }
        }
        sendMessage(message.chat.id, text)
    }
}
