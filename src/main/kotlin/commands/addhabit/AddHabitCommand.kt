package commands.addhabit

import services.HabitService
import Strings
import dev.inmo.tgbotapi.extensions.api.send.sendMessage
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContext
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onCommand
import dev.inmo.tgbotapi.types.IdChatIdentifier
import dto.Habit
import dto.HabitReminder
import dto.HabitType
import lang
import tz
import userId

/**
 * /addhabit dialog: collects the habit name, type and log mode, delegates the type-specific
 * fields to the matching flow (see [checkFlow] / [timerFlow] / [quantityFlow]), then gathers
 * reminders and saves. Each flow returns null when the user cancelled (having sent its own
 * message), which aborts the whole dialog.
 */
fun BehaviourContext.registerAddHabitCommand() {
    onCommand("addhabit") { message ->
        val chatId = message.chat.id
        if (data.tz == null) {
            sendMessage(chatId, Strings.tzRequiredAddHabit(data.lang))
            return@onCommand
        }

        sendMessage(chatId, Strings.sendHabitName(data.lang))
        val nameText = nextText()
        if (nameText.isBlank() || nameText.startsWith("/")) {
            sendMessage(chatId, Strings.cancelled(data.lang))
            return@onCommand
        }

        val typeChoice = pickFromKeyboard(chatId, Strings.pickHabitType(data.lang), typeKeyboard(data.lang), TYPE_PREFIX)
            ?: run { sendMessage(chatId, Strings.cancelled(data.lang)); return@onCommand }
        val type = HabitType.entries.firstOrNull { it.value == typeChoice }
            ?: run { sendMessage(chatId, Strings.cancelled(data.lang)); return@onCommand }

        val logChoice = pickFromKeyboard(chatId, Strings.pickLogMode(data.lang), logModeKeyboard(data.lang), LOG_PREFIX)
            ?: run { sendMessage(chatId, Strings.cancelled(data.lang)); return@onCommand }
        val logOnly = logChoice == LOG_ON

        val draft = when (type) {
            HabitType.CHECK -> checkFlow(chatId, logOnly)
            HabitType.TIMER -> timerFlow(chatId, logOnly)
            HabitType.QUANTITY -> quantityFlow(chatId, logOnly)
        } ?: return@onCommand

        val reminders = if (type == HabitType.TIMER) emptyList()
        else collectReminders(chatId) ?: return@onCommand

        // A check habit must have something to track: a schedule and/or ad-hoc check-ins.
        if (type == HabitType.CHECK && reminders.isEmpty() && !draft.allowAdHoc) {
            sendMessage(chatId, Strings.checkNeedsScheduleOrAdHoc(data.lang))
            return@onCommand
        }

        val habit = HabitService.addHabit(
            Habit(
                userId = data.userId,
                name = nameText,
                type = type,
                dailyTarget = draft.dailyTarget,
                unit = draft.unit,
                direction = draft.direction,
                reminders = reminders,
                params = draft.params,
                logOnly = logOnly,
                allowAdHoc = draft.allowAdHoc,
            )
        )
        sendMessage(chatId, Strings.habitAddedDetailed(data.lang, habit))
    }
}

/**
 * Reminder-collection loop, shared by all types. Reminders are always optional here; a check
 * habit's "must have a schedule and/or ad-hoc" rule is enforced by the caller after this returns.
 * Returns null (after sending a message) on cancel/error.
 */
private suspend fun BehaviourContext.collectReminders(chatId: IdChatIdentifier): List<HabitReminder>? {
    val reminders = mutableListOf<HabitReminder>()
    while (true) {
        val firstOne = reminders.isEmpty()
        val prompt = if (firstOne) Strings.sendFirstReminderTimeOptional(data.lang)
                     else Strings.sendNextReminderTimeOrDone(data.lang)
        sendMessage(chatId, prompt)
        val timeText = nextText()
        if (timeText.startsWith("/")) {
            sendMessage(chatId, Strings.cancelled(data.lang)); return null
        }
        if (!firstOne && isDone(timeText)) break
        if (firstOne && isSkipped(timeText)) break

        val offsetMinutes = parseTime(timeText)
        if (offsetMinutes == null) {
            sendMessage(chatId, Strings.invalidTime(data.lang)); return null
        }
        if (reminders.any { it.offsetMinutes == offsetMinutes }) {
            sendMessage(chatId, Strings.duplicateTime(data.lang)); continue
        }

        val displayTime = Strings.formatDisplayTime(offsetMinutes)
        sendMessage(chatId, Strings.sendReminderDaysFor(data.lang, displayTime))
        val daysText = nextText()
        if (daysText.startsWith("/")) {
            sendMessage(chatId, Strings.cancelled(data.lang)); return null
        }
        val days = if (isSkipped(daysText)) {
            emptyList()
        } else {
            parseDays(daysText) ?: run {
                sendMessage(chatId, Strings.invalidDays(data.lang)); return null
            }
        }

        reminders += HabitReminder(offsetMinutes = offsetMinutes, days = days)
    }
    return reminders
}
