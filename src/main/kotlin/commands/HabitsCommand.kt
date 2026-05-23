package commands

import HabitService
import Keyboards
import Strings
import dev.inmo.tgbotapi.extensions.api.send.sendMessage
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContext
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onCommand
import senderLang
import senderUserId

fun BehaviourContext.registerHabitsCommand() {
    onCommand("habits") { message ->
        val userId = message.senderUserId() ?: return@onCommand
        val lang = message.senderLang()
        val habits = HabitService.listActive(userId)
        if (habits.isEmpty()) {
            sendMessage(message.chat.id, Strings.noHabits(lang))
            return@onCommand
        }

        val text = buildString {
            appendLine(Strings.yourHabits(lang))
            habits.forEach { habit ->
                val times = habit.reminders.joinToString(", ") { it.format(Keyboards.TIME_FMT) }
                val flag = if (habit.pausedAt != null) " ⏸" else ""
                appendLine("• ${habit.name}$flag — $times")
            }
        }
        sendMessage(message.chat.id, text)
    }
}
