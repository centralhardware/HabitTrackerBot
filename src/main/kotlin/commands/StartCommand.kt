package commands

import dev.inmo.tgbotapi.extensions.api.send.sendMessage
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContext
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onCommand

fun BehaviourContext.registerStartCommand() {
    onCommand("start") { message ->
        sendMessage(
            chatId = message.chat.id,
            text = """
                Habit tracker bot.

                Commands:
                /addhabit — add a habit (interactive)
                /habits — list active habits
                /removehabit — remove a habit
                /pause — pause reminders for a habit
                /resume — resume a paused habit
                /checkin — today's check-ins
                /stats — statistics
                /tz — show or set your timezone (e.g. /tz Europe/Moscow)
            """.trimIndent()
        )
    }
}
