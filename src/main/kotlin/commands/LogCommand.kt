package commands

import Keyboards
import Lang
import Strings
import dev.inmo.tgbotapi.extensions.api.send.sendMessage
import dev.inmo.tgbotapi.extensions.api.send.sendRichMessage
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContext
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onCommand
import dev.inmo.tgbotapi.types.IdChatIdentifier
import dev.inmo.tgbotapi.types.buttons.InlineKeyboardMarkup
import dev.inmo.tgbotapi.types.rich.InputRichMessageMarkdown
import dev.inmo.tgbotapi.types.rich.RichBlock
import dev.inmo.tgbotapi.types.rich.RichBlockTable
import dev.inmo.tgbotapi.types.rich.buildRichBlocks
import dev.inmo.tgbotapi.types.rich.toRichMarkdown
import dto.Track
import lang
import services.CheckInService
import services.TrackService
import userId

const val RECENT_PAGE_SIZE = 7

/**
 * Renders page [page] of a user's recent check-ins as a rich message (a table) plus an optional pager
 * keyboard, or `null` blocks when there is nothing to show. Shared by the `/log` command and its
 * pagination callback so both stay in sync.
 */
fun recentLogView(lang: Lang, userId: Long, page: Int): Pair<List<RichBlock>?, InlineKeyboardMarkup?> {
    val result = CheckInService.recentCheckins(userId, page, RECENT_PAGE_SIZE)
    if (result.items.isEmpty()) {
        // Empty first page = nothing logged; an empty later page can't normally be reached.
        return null to null
    }
    val trackCache = HashMap<Long, Track?>()
    fun track(id: Long): Track? = trackCache.getOrPut(id) { TrackService.findById(id, userId) }

    val header = listOf(
        boldCell(Strings.colDate(lang)),
        boldCell(Strings.colTrack(lang)),
        boldCell(Strings.colValue(lang)),
        boldCell(Strings.colComment(lang)),
    )
    val rows = result.items.map { rc ->
        val t = track(rc.trackId)
        listOf(
            plainCell(rc.date.toString()),
            plainCell(Strings.recentTrackName(t, rc)),
            plainCell(Strings.recentValueCell(lang, t, rc)),
            plainCell(rc.comment ?: ""),
        )
    }

    val blocks = buildRichBlocks {
        heading(Strings.recentHeader(lang), 2)
        +RichBlockTable(listOf(header) + rows, isBordered = true, isStriped = true)
    }
    return blocks to Keyboards.recentPager(result.page, result.page > 0, result.hasNext, lang)
}

/** Sends (or, on pagination, resends — rich messages can't be edited in place) the /log listing. */
suspend fun BehaviourContext.sendRecentLog(chatId: IdChatIdentifier, page: Int) {
    val (blocks, keyboard) = recentLogView(data.lang, data.userId, page)
    if (blocks == null) {
        sendMessage(chatId, Strings.noRecentCheckins(data.lang))
        return
    }
    sendRichMessage(chatId, InputRichMessageMarkdown(blocks.toRichMarkdown()), replyMarkup = keyboard)
}

fun BehaviourContext.registerLogCommand() {
    onCommand("log") { message ->
        sendRecentLog(message.chat.id, 0)
    }
}
