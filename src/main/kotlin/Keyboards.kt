import dev.inmo.tgbotapi.types.buttons.InlineKeyboardButtons.CallbackDataInlineKeyboardButton
import dev.inmo.tgbotapi.types.buttons.InlineKeyboardMarkup
import dto.Habit
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object Keyboards {

    fun checkIn(reminderId: Long, date: LocalDate, lang: Lang): InlineKeyboardMarkup {
        return InlineKeyboardMarkup(
            listOf(
                listOf(
                    CallbackDataInlineKeyboardButton(Strings.btnDone(lang), "ci|$reminderId|$date|done"),
                    CallbackDataInlineKeyboardButton(Strings.btnSkip(lang), "ci|$reminderId|$date|skip")
                )
            )
        )
    }

    fun logPlus(habitId: Long, date: LocalDate, lang: Lang): InlineKeyboardMarkup {
        return InlineKeyboardMarkup(
            listOf(
                listOf(
                    CallbackDataInlineKeyboardButton(Strings.btnPlusOne(lang), "lg|$habitId|$date|1"),
                    CallbackDataInlineKeyboardButton(Strings.btnPlusComment(lang), "lg|$habitId|$date|c"),
                    CallbackDataInlineKeyboardButton(Strings.btnDelete(lang), "lg|$habitId|$date|del")
                )
            )
        )
    }

    /** Start/stop control for a timer habit. Payload: `tm|<habitId>|<date>|start|stop|stopc`. */
    fun timerControl(habitId: Long, running: Boolean, date: LocalDate, lang: Lang): InlineKeyboardMarkup {
        val row = if (running)
            listOf(
                CallbackDataInlineKeyboardButton(Strings.btnTimerStop(lang), "tm|$habitId|$date|stop"),
                CallbackDataInlineKeyboardButton(Strings.btnTimerStopComment(lang), "tm|$habitId|$date|stopc"),
            )
        else
            listOf(CallbackDataInlineKeyboardButton(Strings.btnTimerStart(lang), "tm|$habitId|$date|start"))
        return InlineKeyboardMarkup(listOf(row))
    }

    /** Duration choices shown after a habit is picked for pausing. Payload: `pd|<habitId>|<days>` (0 = indefinite). */
    fun pauseDurations(habitId: Long, lang: Lang): InlineKeyboardMarkup {
        return InlineKeyboardMarkup(
            listOf(
                listOf(
                    CallbackDataInlineKeyboardButton(Strings.btnPauseDay(lang), "pd|$habitId|1"),
                    CallbackDataInlineKeyboardButton(Strings.btnPause3Days(lang), "pd|$habitId|3"),
                    CallbackDataInlineKeyboardButton(Strings.btnPauseWeek(lang), "pd|$habitId|7"),
                ),
                listOf(
                    CallbackDataInlineKeyboardButton(Strings.btnPauseMonth(lang), "pd|$habitId|30"),
                    CallbackDataInlineKeyboardButton(Strings.btnPauseForever(lang), "pd|$habitId|0"),
                ),
                listOf(
                    CallbackDataInlineKeyboardButton(Strings.btnPauseCustom(lang), "pc|$habitId"),
                ),
            )
        )
    }

    fun pickHabit(prefix: String, habits: List<Habit>, icon: String): InlineKeyboardMarkup {
        return InlineKeyboardMarkup(
            habits.map { habit ->
                listOf(
                    CallbackDataInlineKeyboardButton("$icon ${habit.name}", "$prefix|${habit.id}")
                )
            }
        )
    }

    /** Field choices shown after a habit is picked for param deletion. Payload: `dp|<paramId>`. */
    fun pickParam(params: List<dto.HabitParam>, lang: Lang): InlineKeyboardMarkup {
        return InlineKeyboardMarkup(
            params.map { p ->
                listOf(
                    CallbackDataInlineKeyboardButton("🗑 ${p.name.orEmpty()}", "dp|${p.id}")
                )
            }
        )
    }

    /** Content toggles + relink control for the calendar feed. Payload: `cal|tc|tr|new`. */
    fun calendar(includeCheckins: Boolean, includeReminders: Boolean, lang: Lang): InlineKeyboardMarkup {
        fun mark(on: Boolean) = if (on) "✅" else "⬜"
        return InlineKeyboardMarkup(
            listOf(
                listOf(CallbackDataInlineKeyboardButton("${mark(includeCheckins)} ${Strings.calCheckins(lang)}", "cal|tc")),
                listOf(CallbackDataInlineKeyboardButton("${mark(includeReminders)} ${Strings.calReminders(lang)}", "cal|tr")),
                listOf(CallbackDataInlineKeyboardButton(Strings.calNewLink(lang), "cal|new")),
            )
        )
    }
}
