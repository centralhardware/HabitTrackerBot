import dev.inmo.tgbotapi.extensions.api.edit.text.editMessageText
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContext
import dev.inmo.tgbotapi.types.asTelegramMessageId
import dev.inmo.tgbotapi.types.toChatId
import dto.CheckinStatus
import services.ReminderMessageService
import java.time.LocalDate

/**
 * Rewrites every reminder message we remembered for a (reminderId, date) check-in to
 * reflect its resolved [status] — swapping the ⏳ marker for ✅/❌ and dropping the inline
 * keyboard — then forgets them. Lets a done/skip recorded through any single button (or by
 * auto-skip) settle all the duplicate reminder messages for the same check-in at once.
 *
 * [excludeMessageId] skips a message the caller already edited (the clicked one), avoiding
 * a redundant "message is not modified" edit.
 */
suspend fun BehaviourContext.resolveCheckInMessages(
    reminderId: Long,
    date: LocalDate,
    status: CheckinStatus,
    excludeMessageId: Long? = null,
) {
    val messages = ReminderMessageService.forCheckIn(reminderId, date)
    val icon = checkInIcon(status)
    messages.forEach { m ->
        if (m.messageId == excludeMessageId) return@forEach
        runCatching {
            editMessageText(
                chatId = m.userId.toChatId(),
                messageId = m.messageId.asTelegramMessageId(),
                text = resolvedReminderText(m.text, icon),
            )
        }
    }
    ReminderMessageService.forget(reminderId, date)
}

fun checkInIcon(status: CheckinStatus): String =
    if (status == CheckinStatus.DONE) "✅" else "❌"

/** Swaps the pending ⏳ marker for the resolved icon, or prefixes it if no marker is present. */
fun resolvedReminderText(original: String, icon: String): String =
    if (original.contains("⏳")) original.replaceFirst("⏳", icon)
    else "$icon $original"
