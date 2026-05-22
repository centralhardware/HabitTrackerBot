package commands

import CheckInService
import dev.inmo.tgbotapi.extensions.api.send.sendMessage
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContext
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onCommand
import senderUserId

fun BehaviourContext.registerStatsCommand() {
    onCommand("stats") { message ->
        val userId = message.senderUserId() ?: return@onCommand
        val stats = CheckInService.userStats(userId)
        if (stats.isEmpty()) {
            sendMessage(message.chat.id, "No habits to report on.")
            return@onCommand
        }

        val text = buildString {
            appendLine("Stats:")
            stats.forEach { s ->
                val total = s.doneCount + s.skipCount
                val rate = if (total > 0) "%.0f%%".format(s.doneCount * 100.0 / total) else "—"
                appendLine("• ${s.name}")
                appendLine("    ✅ ${s.doneCount}   ❌ ${s.skipCount}   completion: $rate")
                appendLine("    🔥 streak: ${s.streak} day(s)")
            }
        }
        sendMessage(message.chat.id, text)
    }
}
