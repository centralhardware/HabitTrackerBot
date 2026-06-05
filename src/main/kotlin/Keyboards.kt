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
}
