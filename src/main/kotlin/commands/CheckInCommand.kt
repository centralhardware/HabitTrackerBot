package commands

import HabitService
import Keyboards
import Strings
import UserSettingsService
import db.CheckInRepository
import dev.inmo.tgbotapi.extensions.api.send.sendMessage
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContext
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onCommand
import dto.HabitStatus
import dto.HabitType
import senderLang
import senderUserId
import java.time.LocalDate

fun BehaviourContext.registerCheckInCommand() {
    onCommand("checkin") { message ->
        val userId = message.senderUserId() ?: return@onCommand
        val lang = message.senderLang()
        val tz = UserSettingsService.getTimezone(userId)
        if (tz == null) {
            sendMessage(message.chat.id, Strings.tzRequiredCheckIn(lang))
            return@onCommand
        }
        val today = LocalDate.now(tz)
        val yesterday = today.minusDays(1)
        val scheduled = CheckInRepository.pendingCheckIns(userId, yesterday, today)
        val active = HabitService.listActive(userId).filter { it.status == HabitStatus.ACTIVE }
        val counters = active.filter { it.type == HabitType.COUNTER }
        val quantitySingles = active.filter { it.type == HabitType.QUANTITY && it.groupId == null }
        val quantityGroups = active.filter { it.isGroupRoot }

        if (scheduled.isEmpty() && counters.isEmpty() && quantitySingles.isEmpty() && quantityGroups.isEmpty()) {
            sendMessage(message.chat.id, Strings.nothingToCheckIn(lang))
            return@onCommand
        }

        sendMessage(message.chat.id, Strings.pendingCheckIns(lang))

        scheduled.forEach { item ->
            val time = item.reminderTime.format(Keyboards.TIME_FMT)
            sendMessage(
                chatId = message.chat.id,
                text = "⏳ ${item.date} $time — ${item.name}",
                replyMarkup = Keyboards.checkIn(item.reminderId, item.date, lang)
            )
        }

        counters.forEach { habit ->
            val current = CheckInRepository.todayCounterCount(habit.id, today)
            sendMessage(
                chatId = message.chat.id,
                text = Strings.counterLine(lang, habit, current, today),
                replyMarkup = Keyboards.logPlus(habit.id, today, lang)
            )
        }

        quantitySingles.forEach { habit ->
            val current = CheckInRepository.todayQuantitySum(habit.id, today)
            sendMessage(
                chatId = message.chat.id,
                text = Strings.quantityLine(lang, habit, current, today),
                replyMarkup = Keyboards.logQuantity(habit.id, today, lang)
            )
        }

        quantityGroups.forEach { root ->
            val perField = root.fields.associateWith { f ->
                CheckInRepository.todayQuantitySum(f.id, today)
            }
            sendMessage(
                chatId = message.chat.id,
                text = Strings.quantityGroupLine(lang, root, perField, today),
                replyMarkup = Keyboards.logQuantity(root.id, today, lang)
            )
        }
    }
}
