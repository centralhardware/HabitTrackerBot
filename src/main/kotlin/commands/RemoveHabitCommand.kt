package commands

import services.HabitService
import Keyboards
import Strings
import dev.inmo.tgbotapi.extensions.api.send.sendMessage
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContext
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onCommand
import lang
import userId

fun BehaviourContext.registerRemoveHabitCommand() {
    onCommand("removehabit") { message ->
        val habits = HabitService.listActive(data.userId)
        if (habits.isEmpty()) {
            sendMessage(message.chat.id, Strings.nothingToRemove(data.lang))
            return@onCommand
        }
        sendMessage(
            chatId = message.chat.id,
            text = Strings.pickHabitToRemove(data.lang),
            replyMarkup = Keyboards.pickHabit("rm", habits, "🗑")
        )
    }
}
