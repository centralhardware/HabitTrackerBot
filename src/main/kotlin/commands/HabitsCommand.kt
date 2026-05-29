package commands

import HabitService
import Keyboards
import Strings
import dev.inmo.tgbotapi.extensions.api.send.sendMessage
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContext
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onCommand
import dto.HabitStatus
import dto.HabitType
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
                val flag = if (habit.status == HabitStatus.PAUSED) " ⏸" else ""
                val typeLabel = Strings.habitTypeLabel(lang, habit)
                val times = habit.reminders.joinToString(", ") { it.format(Keyboards.TIME_FMT) }
                    .let { t -> if (t.isNotEmpty() && habit.reminderDays.isNotEmpty()) "$t (${Strings.formatDays(lang, habit.reminderDays)})" else t }
                val tail = when {
                    times.isNotEmpty() -> " — $times"
                    habit.type == HabitType.SCHEDULED -> ""
                    habit.isGroupRoot -> ""
                    else -> " — ${Strings.noReminders(lang)}"
                }
                appendLine("• ${habit.name}$flag [$typeLabel]$tail")
                if (habit.isGroupRoot) {
                    habit.fields.forEach { f ->
                        val unit = f.unit?.let { " $it" } ?: ""
                        val target = f.dailyTarget?.let { " — ${Strings.formatAmount(it)}$unit/day" } ?: ""
                        appendLine("    – ${f.name}$target")
                    }
                }
            }
        }
        sendMessage(message.chat.id, text)
    }
}
