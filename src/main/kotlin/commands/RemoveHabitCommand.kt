package commands

import HabitService
import Keyboards
import dev.inmo.tgbotapi.extensions.api.send.sendMessage
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContext
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onCommand
import senderUserId

fun BehaviourContext.registerRemoveHabitCommand() {
    onCommand("removehabit") { message ->
        val userId = message.senderUserId() ?: return@onCommand
        val habits = HabitService.listActive(userId)
        if (habits.isEmpty()) {
            sendMessage(message.chat.id, "Nothing to remove.")
            return@onCommand
        }
        sendMessage(
            chatId = message.chat.id,
            text = "Pick a habit to remove:",
            replyMarkup = Keyboards.pickHabit("rm", habits, "🗑")
        )
    }
}
