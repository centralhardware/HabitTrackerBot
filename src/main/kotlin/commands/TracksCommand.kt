package commands

import services.TrackService
import Keyboards
import Strings
import dev.inmo.tgbotapi.extensions.api.send.sendMessage
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContext
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onCommand
import dto.TrackStatus
import dto.TrackType
import lang
import userId

fun BehaviourContext.registerTracksCommand() {
    onCommand("tracks") { message ->
        val tracks = TrackService.listActive(data.userId)
        if (tracks.isEmpty()) {
            sendMessage(message.chat.id, Strings.noTracks(data.lang))
            return@onCommand
        }

        val text = buildString {
            appendLine(Strings.yourTracks(data.lang))
            tracks.forEach { track ->
                val flag = if (track.status == TrackStatus.PAUSED) " ⏸" else ""
                val typeLabel = Strings.trackTypeLabel(data.lang, track) + if (track.logOnly) Strings.logBadge(data.lang) else ""
                val times = track.reminders.joinToString(", ") { rem ->
                    val d = if (rem.days.isNotEmpty()) " (${Strings.formatDays(data.lang, rem.days)})" else ""
                    "${Strings.formatDisplayTime(rem.offsetMinutes)}$d"
                }
                val tail = when {
                    times.isNotEmpty() -> " — $times"
                    track.type == TrackType.CHECK -> ""
                    track.multiField -> ""
                    else -> " — ${Strings.noReminders(data.lang)}"
                }
                appendLine("• ${track.name}$flag [$typeLabel]$tail")
                if (track.multiField) {
                    track.params.forEach { f ->
                        val unit = f.unit?.let { " $it" } ?: ""
                        val target = f.dailyTarget?.let { " — ${Strings.formatAmount(it)}$unit/day" } ?: ""
                        appendLine("    – ${f.name ?: ""}$target")
                    }
                }
            }
        }
        sendMessage(message.chat.id, text)
    }
}
