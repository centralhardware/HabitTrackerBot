import dev.inmo.tgbotapi.types.buttons.InlineKeyboardButtons.CallbackDataInlineKeyboardButton
import dev.inmo.tgbotapi.types.buttons.InlineKeyboardMarkup
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object Keyboards {

    val TIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun checkIn(reminderId: Long, date: LocalDate): InlineKeyboardMarkup {
        return InlineKeyboardMarkup(
            listOf(
                listOf(
                    CallbackDataInlineKeyboardButton("✅ Done", "ci|$reminderId|$date|done"),
                    CallbackDataInlineKeyboardButton("❌ Skip", "ci|$reminderId|$date|skip")
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
