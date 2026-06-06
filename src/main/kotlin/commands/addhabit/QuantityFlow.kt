package commands.addhabit

import Strings
import dev.inmo.tgbotapi.extensions.api.send.sendMessage
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContext
import dev.inmo.tgbotapi.types.IdChatIdentifier
import dto.HabitParam
import dto.ParamType
import lang

/**
 * Quantity habit: one or more fields ("params"). Each field is NUMBER (with optional target,
 * unit, direction) or free TEXT. The loop keeps asking for fields until the user signals "done"
 * (only after at least one). Returns null (after sending a message) on cancel/invalid input.
 */
suspend fun BehaviourContext.quantityFlow(chatId: IdChatIdentifier, logOnly: Boolean): HabitDraft? {
    val fields = mutableListOf<HabitParam>()
    while (true) {
        val nextLabel = if (fields.isEmpty()) Strings.sendFirstFieldName(data.lang)
                        else Strings.sendNextFieldNameOrDone(data.lang)
        sendMessage(chatId, nextLabel)
        val fname = nextText()
        if (fname.startsWith("/")) {
            sendMessage(chatId, Strings.cancelled(data.lang)); return null
        }
        if (fields.isNotEmpty() && (fname.equals("done", ignoreCase = true) ||
                                    fname.equals("готово", ignoreCase = true) ||
                                    fname == "-")) break
        if (fname.isBlank()) {
            sendMessage(chatId, Strings.cancelled(data.lang)); return null
        }

        val ptypeChoice = pickFromKeyboard(
            chatId,
            Strings.pickParamType(data.lang),
            paramTypeKeyboard(data.lang),
            PTYPE_PREFIX
        ) ?: run {
            sendMessage(chatId, Strings.cancelled(data.lang)); return null
        }
        val isText = ptypeChoice == PTYPE_TEXT

        if (isText) {
            fields.add(HabitParam(id = 0, name = fname.take(64), paramType = ParamType.TEXT))
        } else {
            var fTarget: Double? = null
            if (!logOnly) {
                sendMessage(chatId, Strings.sendDailyTargetValue(data.lang))
                val tRaw = nextText()
                if (tRaw.startsWith("/")) {
                    sendMessage(chatId, Strings.cancelled(data.lang)); return null
                }
                fTarget = if (isSkipped(tRaw)) null else {
                    val v = tRaw.replace(',', '.').toDoubleOrNull()
                    if (v == null || v <= 0.0 || v.isNaN() || v.isInfinite()) {
                        sendMessage(chatId, Strings.invalidTargetValue(data.lang)); return null
                    }
                    v
                }
            }

            sendMessage(chatId, Strings.sendUnit(data.lang))
            val uRaw = nextText()
            if (uRaw.startsWith("/")) {
                sendMessage(chatId, Strings.cancelled(data.lang)); return null
            }
            val fUnit = if (isSkipped(uRaw)) null else uRaw.take(16)

            var fDir: dto.Direction? = null
            if (!logOnly) {
                fDir = when (val d = pickDirection(chatId)) {
                    is DirPick.Picked -> d.direction
                    DirPick.Cancelled -> { sendMessage(chatId, Strings.cancelled(data.lang)); return null }
                }
            }

            fields.add(HabitParam(id = 0, name = fname.take(64), unit = fUnit, direction = fDir, dailyTarget = fTarget, paramType = ParamType.NUMBER))
        }
    }

    if (fields.isEmpty()) {
        sendMessage(chatId, Strings.cancelled(data.lang)); return null
    }
    return HabitDraft(params = fields)
}
