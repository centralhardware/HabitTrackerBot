package commands

import Config
import Keyboards
import Strings
import dev.inmo.tgbotapi.extensions.api.answers.answerCallbackQuery
import dev.inmo.tgbotapi.extensions.api.edit.text.editMessageText
import dev.inmo.tgbotapi.extensions.api.send.sendMessage
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContext
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onCommand
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onMessageDataCallbackQuery
import dev.inmo.tgbotapi.types.buttons.InlineKeyboardMarkup
import dev.inmo.tgbotapi.types.queries.callback.MessageDataCallbackQuery
import dto.CalendarSubscription
import lang
import services.CalendarTokenService
import userId

fun BehaviourContext.registerCalendarCommand() {
    onCommand("calendar") { message ->
        // Issue a link on first use; afterwards just show the toggles (the token can't be reshown,
        // so the user regenerates with "New link" if they need the URL again).
        val existing = CalendarTokenService.get(data.userId)
        if (existing == null) {
            val issued = CalendarTokenService.issue(data.userId)
            val (text, kb) = render(issued.subscription, url(issued.token))
            sendMessage(message.chat.id, text, replyMarkup = kb)
        } else {
            val (text, kb) = render(existing, url = null)
            sendMessage(message.chat.id, text, replyMarkup = kb)
        }
    }

    onCommand("calendar_off") { message ->
        val removed = CalendarTokenService.revoke(data.userId)
        sendMessage(message.chat.id, if (removed) Strings.calOff(data.lang) else Strings.calNoSub(data.lang))
    }

    onMessageDataCallbackQuery(Regex("^cal\\|.*")) { handleCalendar(it) }
}

private suspend fun BehaviourContext.handleCalendar(query: MessageDataCallbackQuery) {
    val action = query.data.removePrefix("cal|")
    val sub = CalendarTokenService.get(data.userId) ?: run {
        answerCallbackQuery(query, text = Strings.calNoSub(data.lang))
        return
    }
    when (action) {
        "tc" -> {
            CalendarTokenService.setContent(data.userId, !sub.includeCheckins, sub.includeReminders)
            rerender(query, url = null)
            answerCallbackQuery(query, text = Strings.calUpdated(data.lang))
        }
        "tr" -> {
            CalendarTokenService.setContent(data.userId, sub.includeCheckins, !sub.includeReminders)
            rerender(query, url = null)
            answerCallbackQuery(query, text = Strings.calUpdated(data.lang))
        }
        "new" -> {
            val issued = CalendarTokenService.issue(data.userId)
            rerender(query, url = url(issued.token))
            answerCallbackQuery(query, text = Strings.calLinkReset(data.lang))
        }
        else -> answerCallbackQuery(query)
    }
}

/** Re-fetches the (possibly just-changed) subscription and edits the message in place. */
private suspend fun BehaviourContext.rerender(query: MessageDataCallbackQuery, url: String?) {
    val sub = CalendarTokenService.get(data.userId) ?: return
    val (text, kb) = render(sub, url)
    runCatching {
        editMessageText(
            chatId = query.message.chat.id,
            messageId = query.message.messageId,
            text = text,
            replyMarkup = kb,
        )
    }
}

private fun BehaviourContext.render(sub: CalendarSubscription, url: String?): Pair<String, dev.inmo.tgbotapi.types.buttons.InlineKeyboardMarkup> {
    val l = data.lang
    val text = buildString {
        appendLine(Strings.calTitle(l))
        if (url != null) {
            appendLine()
            appendLine(Strings.calHowTo(l))
            appendLine(url)
        }
        appendLine()
        append(Strings.calToggleHint(l))
    }
    return text to Keyboards.calendar(sub.includeCheckins, sub.includeReminders, l)
}

private fun url(token: String): String = "${Config.CALENDAR_PUBLIC_URL.trimEnd('/')}/$token.ics"
