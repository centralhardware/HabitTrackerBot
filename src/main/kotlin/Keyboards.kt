import dev.inmo.tgbotapi.types.buttons.InlineKeyboardButtons.CallbackDataInlineKeyboardButton
import dev.inmo.tgbotapi.types.buttons.InlineKeyboardMarkup
import dto.Habit
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object Keyboards {

    val TIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

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
