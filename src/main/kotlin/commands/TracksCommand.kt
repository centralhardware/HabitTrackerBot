package commands

import services.TrackService
import Keyboards
import Strings
import dev.inmo.tgbotapi.extensions.api.send.sendMessage
import dev.inmo.tgbotapi.extensions.api.send.sendRichMessage
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContext
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onCommand
import dev.inmo.tgbotapi.types.rich.InputRichMessageMarkdown
import dev.inmo.tgbotapi.types.rich.RichBlockTable
import dev.inmo.tgbotapi.types.rich.buildRichBlocks
import dev.inmo.tgbotapi.types.rich.buildRichText
import dev.inmo.tgbotapi.types.rich.toRichMarkdown
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

        val header = listOf(
            boldCell(Strings.colTrack(data.lang)),
            boldCell(Strings.colType(data.lang)),
            boldCell(Strings.colReminders(data.lang)),
            boldCell(Strings.colFields(data.lang)),
        )
        val rows = tracks.map { track ->
            val paused = if (track.status == TrackStatus.PAUSED) " ⏸" else ""
            val typeLabel = Strings.trackTypeLabel(data.lang, track) + if (track.logOnly) Strings.logBadge(data.lang) else ""
            val times = track.reminders.joinToString(", ") { rem ->
                val d = if (rem.days.isNotEmpty()) " (${Strings.formatDays(data.lang, rem.days)})" else ""
                "${Strings.formatDisplayTime(rem.offsetMinutes)}$d"
            }
            val reminders = when {
                times.isNotEmpty() -> times
                track.type == TrackType.CHECK || track.multiField -> ""
                else -> Strings.noReminders(data.lang)
            }
            val fields = if (track.multiField) {
                track.params.joinToString(", ") { f ->
                    val unit = f.unit?.let { " $it" } ?: ""
                    val target = f.dailyTarget?.let { " (${Strings.formatAmount(it)}$unit/day)" } ?: ""
                    "${f.name ?: ""}$target"
                }
            } else ""
            listOf(
                cell(buildRichText { bold(track.name); if (paused.isNotEmpty()) plain(paused) }),
                plainCell(typeLabel),
                plainCell(reminders),
                plainCell(fields),
            )
        }

        val blocks = buildRichBlocks {
            heading(Strings.yourTracks(data.lang), 2)
            +RichBlockTable(listOf(header) + rows, isBordered = true, isStriped = true)
        }

        sendRichMessage(message.chat.id, InputRichMessageMarkdown(blocks.toRichMarkdown()))
    }
}
