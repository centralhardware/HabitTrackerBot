package commands

import HabitService
import Keyboards
import dev.inmo.tgbotapi.extensions.api.send.sendMessage
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContext
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onCommand
import senderUserId

fun BehaviourContext.registerHabitsCommand() {
    onCommand("habits") { message ->
        val userId = message.senderUserId() ?: return@onCommand
        val habits = HabitService.listActive(userId)
        if (habits.isEmpty()) {
            sendMessage(message.chat.id, "No habits yet. Add one with /addhabit.")
            return@onCommand
        }

        val text = buildString {
            appendLine("Your habits:")
            habits.forEach { habit ->
                val times = habit.reminders.joinToString(", ") { it.format(Keyboards.TIME_FMT) }
                val flag = if (habit.pausedAt != null) " ⏸" else ""
                appendLine("• ${habit.name}$flag — $times")
            }
        }
        sendMessage(message.chat.id, text)
    }
}
