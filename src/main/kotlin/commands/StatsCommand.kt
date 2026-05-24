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
                when (s) {
                    is CheckInService.HabitStat.Scheduled -> {
                        val total = s.doneCount + s.skipCount
                        val rate = if (total > 0) "%.0f%%".format(s.doneCount * 100.0 / total) else "—"
                        appendLine("    ✅ ${s.doneCount}   ❌ ${s.skipCount}   ${Strings.statsCompletion(lang)}: $rate")
                        appendLine("    ${Strings.statsStreak(lang, s.streak)}")
                    }
                    is CheckInService.HabitStat.Counter.WithTarget -> {
                        Strings.statsCounterTarget(lang, s).forEach { appendLine("    $it") }
                    }
                    is CheckInService.HabitStat.Counter.Trend -> {
                        Strings.statsCounterTrend(lang, s).forEach { appendLine("    $it") }
                    }
                    is CheckInService.HabitStat.Counter.Plain -> {
                        appendLine("    ${Strings.statsCounterPlain(lang, s)}")
                    }
                    is CheckInService.HabitStat.Quantity.WithTarget -> {
                        Strings.statsQuantityTarget(lang, s).forEach { appendLine("    $it") }
                    }
                    is CheckInService.HabitStat.Quantity.Trend -> {
                        Strings.statsQuantityTrend(lang, s).forEach { appendLine("    $it") }
                    }
                    is CheckInService.HabitStat.Quantity.Plain -> {
                        appendLine("    ${Strings.statsQuantityPlain(lang, s)}")
                    }
                }
            }
        }
        sendMessage(message.chat.id, text)
    }
}
