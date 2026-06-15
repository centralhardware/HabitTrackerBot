package commands

import services.HabitService
import Keyboards
import Strings
import dev.inmo.tgbotapi.extensions.api.send.sendMessage
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContext
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onCommand
import dto.HabitStatus
import dto.HabitType
import lang
import userId

fun BehaviourContext.registerHabitsCommand() {
    onCommand("habits") { message ->
        val habits = HabitService.listActive(data.userId)
        if (habits.isEmpty()) {
            sendMessage(message.chat.id, Strings.noHabits(data.lang))
            return@onCommand
        }

        val text = buildString {
            appendLine(Strings.yourHabits(data.lang))
            habits.forEach { habit ->
                val flag = if (habit.status == HabitStatus.PAUSED) " ⏸" else ""
                val typeLabel = Strings.habitTypeLabel(data.lang, habit) + if (habit.logOnly) Strings.logBadge(data.lang) else ""
                val times = habit.reminders.joinToString(", ") { rem ->
                    val d = if (rem.days.isNotEmpty()) " (${Strings.formatDays(data.lang, rem.days)})" else ""
                    "${Strings.formatDisplayTime(rem.offsetMinutes)}$d"
                }
                val tail = when {
                    times.isNotEmpty() -> " — $times"
                    habit.type == HabitType.CHECK -> ""
                    habit.multiField -> ""
                    else -> " — ${Strings.noReminders(data.lang)}"
                }
                appendLine("• ${habit.name}$flag [$typeLabel]$tail")
                if (habit.multiField) {
                    habit.params.forEach { f ->
                        val unit = f.unit?.let { " $it" } ?: ""
                        val target = f.dailyTarget?.let { " — ${Strings.formatAmount(it)}$unit/day" } ?: ""
                        appendLine("    – ${f.name ?: ""}$target")
                    }
                }
            }
        }
        sendMessage(message.chat.id, text)
    }
}
