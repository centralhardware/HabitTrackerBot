import dev.inmo.tgbotapi.types.buttons.InlineKeyboardButtons.CallbackDataInlineKeyboardButton
import dev.inmo.tgbotapi.types.buttons.InlineKeyboardMarkup
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object Keyboards {

    val TIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun checkIn(reminderId: Long, date: LocalDate, lang: Lang): InlineKeyboardMarkup {
        return InlineKeyboardMarkup(
            listOf(
                listOf(
                    CallbackDataInlineKeyboardButton(Strings.btnDone(lang), "ci|$reminderId|$date|done"),
                    CallbackDataInlineKeyboardButton(Strings.btnSkip(lang), "ci|$reminderId|$date|skip"),
                    CallbackDataInlineKeyboardButton(Strings.btnDelete(lang), "ci|$reminderId|$date|del")
                )
            )
        )
    }

    fun pickHabit(prefix: String, habits: List<HabitService.Habit>, icon: String): InlineKeyboardMarkup {
        return InlineKeyboardMarkup(
            habits.map { habit ->
                listOf(
                    CallbackDataInlineKeyboardButton("$icon ${habit.name}", "$prefix|${habit.id}")
                )
            }
        )
    }
}
