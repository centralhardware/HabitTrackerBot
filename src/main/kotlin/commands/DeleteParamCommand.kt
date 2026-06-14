package commands

import services.HabitService
import Keyboards
import Strings
import dev.inmo.tgbotapi.extensions.api.send.sendMessage
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContext
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onCommand
import lang
import userId

/** A habit field is deletable only when its row carries a name and removing it still leaves >=1 param. */
private fun hasDeletableParams(habit: dto.Habit) =
    habit.params.size > 1 && habit.params.any { it.name != null }

fun BehaviourContext.registerDeleteParamCommand() {
    onCommand("deleteparam") { message ->
        val habits = HabitService.listActive(data.userId).filter(::hasDeletableParams)
        if (habits.isEmpty()) {
            sendMessage(message.chat.id, Strings.noParamsToDelete(data.lang))
            return@onCommand
        }
        sendMessage(
            chatId = message.chat.id,
            text = Strings.pickHabitForParam(data.lang),
            replyMarkup = Keyboards.pickHabit("dh", habits, "📋")
        )
    }
}
