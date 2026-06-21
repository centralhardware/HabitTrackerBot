package commands.addtrack

import services.TrackService
import Strings
import dev.inmo.tgbotapi.extensions.api.send.sendMessage
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContext
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onCommand
import dev.inmo.tgbotapi.types.IdChatIdentifier
import dto.Track
import dto.TrackReminder
import dto.TrackType
import lang
import tz
import userId

/**
 * /addtrack dialog: collects the track name, type and log mode, delegates the type-specific
 * fields to the matching flow (see [checkFlow] / [timerFlow] / [quantityFlow]), then gathers
 * reminders and saves. Each flow returns null when the user cancelled (having sent its own
 * message), which aborts the whole dialog.
 */
fun BehaviourContext.registerAddTrackCommand() {
    onCommand("addtrack") { message ->
        val chatId = message.chat.id
        if (data.tz == null) {
            sendMessage(chatId, Strings.tzRequiredAddTrack(data.lang))
            return@onCommand
        }

        sendMessage(chatId, Strings.sendTrackName(data.lang))
        val nameText = nextText()
        if (nameText.isBlank() || nameText.startsWith("/")) {
            sendMessage(chatId, Strings.cancelled(data.lang))
            return@onCommand
        }

        val typeChoice = pickFromKeyboard(chatId, Strings.pickTrackType(data.lang), typeKeyboard(data.lang), TYPE_PREFIX)
            ?: run { sendMessage(chatId, Strings.cancelled(data.lang)); return@onCommand }
        val type = TrackType.entries.firstOrNull { it.value == typeChoice }
            ?: run { sendMessage(chatId, Strings.cancelled(data.lang)); return@onCommand }

        val logChoice = pickFromKeyboard(chatId, Strings.pickLogMode(data.lang), logModeKeyboard(data.lang), LOG_PREFIX)
            ?: run { sendMessage(chatId, Strings.cancelled(data.lang)); return@onCommand }
        val logOnly = logChoice == LOG_ON

        val draft = when (type) {
            TrackType.CHECK -> checkFlow(chatId, logOnly)
            TrackType.TIMER -> timerFlow(chatId, logOnly)
            TrackType.QUANTITY -> quantityFlow(chatId, logOnly)
        } ?: return@onCommand

        val reminders = if (type == TrackType.TIMER) emptyList()
        else collectReminders(chatId) ?: return@onCommand

        // A check track must have something to track: a schedule and/or ad-hoc check-ins.
        if (type == TrackType.CHECK && reminders.isEmpty() && !draft.allowAdHoc) {
            sendMessage(chatId, Strings.checkNeedsScheduleOrAdHoc(data.lang))
            return@onCommand
        }

        val track = TrackService.addTrack(
            Track(
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
        sendMessage(chatId, Strings.trackAddedDetailed(data.lang, track))
    }
}

/**
 * Reminder-collection loop, shared by all types. Reminders are always optional here; a check
 * track's "must have a schedule and/or ad-hoc" rule is enforced by the caller after this returns.
 * Returns null (after sending a message) on cancel/error.
 */
private suspend fun BehaviourContext.collectReminders(chatId: IdChatIdentifier): List<TrackReminder>? {
    val reminders = mutableListOf<TrackReminder>()
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

        reminders += TrackReminder(offsetMinutes = offsetMinutes, days = days)
    }
    return reminders
}
