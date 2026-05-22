import dev.inmo.tgbotapi.extensions.api.answers.answerCallbackQuery
import dev.inmo.tgbotapi.extensions.api.edit.text.editMessageText
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContext
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onMessageDataCallbackQuery
import dev.inmo.tgbotapi.types.queries.callback.MessageDataCallbackQuery
import java.time.LocalDate
import java.time.LocalTime

fun BehaviourContext.registerCallbackHandler() {
    onMessageDataCallbackQuery { query ->
        val data = query.data
        val userId = query.user.id.chatId.long
        when {
            data.startsWith("ci|") -> handleCheckIn(query, data, userId)
            data.startsWith("rm|") -> handleRemove(query, data, userId)
            data.startsWith("ps|") -> handlePause(query, data, userId)
            data.startsWith("rs|") -> handleResume(query, data, userId)
            else -> answerCallbackQuery(query)
        }
    }
}

private suspend fun BehaviourContext.handleCheckIn(
    query: MessageDataCallbackQuery,
    data: String,
    userId: Long
) {
    val parts = data.split("|")
    if (parts.size != 5) {
        answerCallbackQuery(query, text = "Bad button")
        return
    }
    val habitId = parts[1].toLongOrNull() ?: run {
        answerCallbackQuery(query, text = "Error")
        return
    }
    val time = try {
        LocalTime.parse(parts[2], Keyboards.TIME_FMT)
    } catch (_: Exception) {
        answerCallbackQuery(query, text = "Bad time")
        return
    }
    val date = try {
        LocalDate.parse(parts[3])
    } catch (_: Exception) {
        answerCallbackQuery(query, text = "Bad date")
        return
    }
    val status = when (parts[4]) {
        "done" -> CheckInService.Status.DONE
        "skip" -> CheckInService.Status.SKIP
        else -> {
            answerCallbackQuery(query, text = "Error")
            return
        }
    }

    CheckInService.record(habitId, userId, time, date, status)

    val icon = if (status == CheckInService.Status.DONE) "✅" else "❌"
    val msg = query.message
    val originalText = (msg.content as? dev.inmo.tgbotapi.types.message.content.TextContent)?.text.orEmpty()
    val newText = if (originalText.isNotEmpty()) {
        originalText.replaceFirst(Regex("^[✅❌⏳]"), icon)
    } else {
        "$icon marked"
    }

    runCatching {
        editMessageText(
            chatId = msg.chat.id,
            messageId = msg.messageId,
            text = newText
        )
    }
    answerCallbackQuery(
        query,
        text = if (status == CheckInService.Status.DONE) "Done ✅" else "Skipped ❌"
    )
}

private suspend fun BehaviourContext.handleRemove(
    query: MessageDataCallbackQuery,
    data: String,
    userId: Long
) {
    val habitId = data.removePrefix("rm|").toLongOrNull() ?: run {
        answerCallbackQuery(query, text = "Error")
        return
    }
    val deleted = HabitService.softDelete(habitId, userId)
    finishAction(query, deleted, doneText = "Removed", missingText = "Not found", editedText = "Habit removed.")
}

private suspend fun BehaviourContext.handlePause(
    query: MessageDataCallbackQuery,
    data: String,
    userId: Long
) {
    val habitId = data.removePrefix("ps|").toLongOrNull() ?: run {
        answerCallbackQuery(query, text = "Error")
        return
    }
    val ok = HabitService.pause(habitId, userId)
    finishAction(query, ok, doneText = "Paused", missingText = "Not found", editedText = "Habit paused.")
}

private suspend fun BehaviourContext.handleResume(
    query: MessageDataCallbackQuery,
    data: String,
    userId: Long
) {
    val habitId = data.removePrefix("rs|").toLongOrNull() ?: run {
        answerCallbackQuery(query, text = "Error")
        return
    }
    val ok = HabitService.resume(habitId, userId)
    finishAction(query, ok, doneText = "Resumed", missingText = "Not found", editedText = "Habit resumed.")
}

private suspend fun BehaviourContext.finishAction(
    query: MessageDataCallbackQuery,
    success: Boolean,
    doneText: String,
    missingText: String,
    editedText: String
) {
    if (success) {
        val msg = query.message
        runCatching {
            editMessageText(chatId = msg.chat.id, messageId = msg.messageId, text = editedText)
        }
        answerCallbackQuery(query, text = doneText)
    } else {
        answerCallbackQuery(query, text = missingText)
    }
}
