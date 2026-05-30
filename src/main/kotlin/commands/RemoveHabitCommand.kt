package commands

import services.HabitService
import Keyboards
import Strings
import dev.inmo.tgbotapi.extensions.api.send.sendMessage
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContext
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onCommand
import senderLang
import senderUserId

fun BehaviourContext.registerRemoveHabitCommand() {
    onCommand("removehabit") { message ->
        val userId = message.senderUserId() ?: return@onCommand
        val lang = message.senderLang()
        val habits = HabitService.listActive(userId)
        if (habits.isEmpty()) {
            sendMessage(message.chat.id, Strings.nothingToRemove(lang))
            return@onCommand
        }
        sendMessage(
            chatId = message.chat.id,
            text = Strings.pickHabitToRemove(lang),
            replyMarkup = Keyboards.pickHabit("rm", habits, "🗑")
        )
    }
}
