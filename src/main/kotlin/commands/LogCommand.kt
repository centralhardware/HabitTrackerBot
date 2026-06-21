package commands

import Keyboards
import Lang
import Strings
import dev.inmo.tgbotapi.extensions.api.send.sendMessage
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContext
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onCommand
import dev.inmo.tgbotapi.types.buttons.InlineKeyboardMarkup
import dto.Track
import lang
import services.CheckInService
import services.TrackService
import userId

const val RECENT_PAGE_SIZE = 7

/**
 * Renders page [page] of a user's recent check-ins into a message body + optional pager keyboard.
 * Shared by the `/log` command and its pagination callback so both stay in sync. Pure (no I/O on
 * the bot side), so the callback can re-render in place.
 */
fun recentLogView(lang: Lang, userId: Long, page: Int): Pair<String, InlineKeyboardMarkup?> {
    val result = CheckInService.recentCheckins(userId, page, RECENT_PAGE_SIZE)
    if (result.items.isEmpty()) {
        // Empty first page = nothing logged; an empty later page can't normally be reached, but
        // fall back to the same message rather than an empty bubble.
        return Strings.noRecentCheckins(lang) to null
    }
    val trackCache = HashMap<Long, Track?>()
    fun track(id: Long): Track? = trackCache.getOrPut(id) { TrackService.findById(id, userId) }
    val text = buildString {
        appendLine(Strings.recentHeader(lang))
        result.items.forEach { appendLine(Strings.recentBlock(lang, track(it.trackId), it)) }
    }.trimEnd()
    return text to Keyboards.recentPager(result.page, result.page > 0, result.hasNext, lang)
}

fun BehaviourContext.registerLogCommand() {
    onCommand("log") { message ->
        val (text, keyboard) = recentLogView(data.lang, data.userId, 0)
        sendMessage(message.chat.id, text, replyMarkup = keyboard)
    }
}
