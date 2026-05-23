import dev.inmo.tgbotapi.types.abstracts.WithOptionalLanguageCode
import dev.inmo.tgbotapi.types.chat.User

enum class Lang {
    EN, RU;

    companion object {
        fun of(user: User?): Lang = ofCode((user as? WithOptionalLanguageCode)?.languageCode)

        fun ofCode(code: String?): Lang =
            if (code != null && code.lowercase().startsWith("ru")) RU else EN

        fun parse(text: String): Lang? = when (text.trim().lowercase()) {
            "en", "english", "англ", "английский" -> EN
            "ru", "russian", "рус", "русский" -> RU
            else -> null
        }
    }
}

object Strings {

    fun startHelp(l: Lang): String = when (l) {
        Lang.EN -> """
            Habit tracker bot.

            Commands:
            /addhabit — add a habit (interactive)
            /habits — list active habits
            /removehabit — remove a habit
            /pause — pause reminders for a habit
            /resume — resume a paused habit
            /checkin — today's check-ins
            /stats — statistics
            /tz — show or set your timezone (e.g. /tz Europe/Moscow)
            /lang — switch language (en/ru)
        """.trimIndent()
        Lang.RU -> """
            Бот для трекинга привычек.

            Команды:
            /addhabit — добавить привычку (интерактивно)
            /habits — список активных привычек
            /removehabit — удалить привычку
            /pause — поставить напоминания на паузу
            /resume — возобновить привычку
            /checkin — чек-ины за сегодня
            /stats — статистика
            /tz — показать или задать часовой пояс (например, /tz Europe/Moscow)
            /lang — сменить язык (en/ru)
        """.trimIndent()
    }

    fun tzRequiredAddHabit(l: Lang) = pick(l,
        "Set your timezone first with /tz <IANA name>, e.g. /tz Europe/Moscow",
        "Сначала задайте часовой пояс через /tz <IANA>, например /tz Europe/Moscow")

    fun tzRequiredCheckIn(l: Lang) = pick(l,
        "Set your timezone first with /tz <IANA name>.",
        "Сначала задайте часовой пояс через /tz <IANA>.")

    fun sendHabitName(l: Lang) = pick(l,
        "Send the habit name:",
        "Отправьте название привычки:")

    fun cancelled(l: Lang) = pick(l, "Cancelled.", "Отменено.")

    fun sendTimes(l: Lang) = pick(l,
        "Send one or more reminder times (HH:MM), space-separated. Example: 09:00 21:00",
        "Отправьте одно или несколько времён напоминаний (ЧЧ:ММ) через пробел. Пример: 09:00 21:00")

    fun invalidTime(l: Lang) = pick(l,
        "Invalid time format. Use HH:MM, e.g. 09:00.",
        "Неверный формат времени. Используйте ЧЧ:ММ, например 09:00.")

    fun noTimes(l: Lang) = pick(l, "No times provided.", "Время не указано.")

    fun habitAdded(l: Lang, name: String, times: String) = pick(l,
        "Added: \"$name\" at $times",
        "Добавлено: «$name» в $times")

    fun noHabits(l: Lang) = pick(l,
        "No habits yet. Add one with /addhabit.",
        "Привычек ещё нет. Добавьте через /addhabit.")

    fun yourHabits(l: Lang) = pick(l, "Your habits:", "Ваши привычки:")

    fun nothingToRemove(l: Lang) = pick(l, "Nothing to remove.", "Удалять нечего.")

    fun pickHabitToRemove(l: Lang) = pick(l, "Pick a habit to remove:", "Выберите привычку для удаления:")

    fun noActiveToPause(l: Lang) = pick(l, "No active habits to pause.", "Нет активных привычек для паузы.")

    fun pickHabitToPause(l: Lang) = pick(l, "Pick a habit to pause:", "Выберите привычку для паузы:")

    fun noPaused(l: Lang) = pick(l, "No paused habits.", "Нет привычек на паузе.")

    fun pickHabitToResume(l: Lang) = pick(l, "Pick a habit to resume:", "Выберите привычку для возобновления:")

    fun nothingToCheckIn(l: Lang) = pick(l, "Nothing to check in.", "Чек-инить нечего.")

    fun pendingCheckIns(l: Lang) = pick(l, "Pending check-ins:", "Ожидают чек-ина:")

    fun noStats(l: Lang) = pick(l, "No habits to report on.", "Не по чем статистику показывать.")

    fun statsHeader(l: Lang) = pick(l, "Stats:", "Статистика:")

    fun statsCompletion(l: Lang) = pick(l, "completion", "выполнение")

    fun statsStreak(l: Lang, days: Int) = pick(l,
        "🔥 streak: $days day(s)",
        "🔥 серия: $days дн.")

    fun tzNotSet(l: Lang) = pick(l,
        "Timezone is not set. Set it with /tz <IANA name>, e.g. /tz Europe/Moscow",
        "Часовой пояс не задан. Задайте через /tz <IANA>, например /tz Europe/Moscow")

    fun tzCurrent(l: Lang, tz: String) = pick(l,
        "Your timezone: $tz\nChange with /tz <IANA name>",
        "Ваш часовой пояс: $tz\nИзменить: /tz <IANA>")

    fun tzUnknown(l: Lang, raw: String) = pick(l,
        "Unknown timezone: $raw. Use IANA names like Europe/Moscow.",
        "Неизвестный часовой пояс: $raw. Используйте IANA, например Europe/Moscow.")

    fun tzInvalid(l: Lang, raw: String) = pick(l,
        "Invalid timezone: $raw.",
        "Некорректный часовой пояс: $raw.")

    fun tzSet(l: Lang, tz: String) = pick(l,
        "Timezone set to $tz.",
        "Часовой пояс установлен: $tz.")

    fun langCurrent(l: Lang, current: Lang) = pick(l,
        "Your language: ${current.name}\nChange with /lang en or /lang ru",
        "Ваш язык: ${current.name}\nИзменить: /lang en или /lang ru")

    fun langInvalid(l: Lang) = pick(l,
        "Unknown language. Use /lang en or /lang ru.",
        "Неизвестный язык. Используйте /lang en или /lang ru.")

    fun langSet(l: Lang) = pick(l, "Language set: EN", "Язык установлен: RU")

    fun btnDone(l: Lang) = pick(l, "✅ Done", "✅ Готово")
    fun btnSkip(l: Lang) = pick(l, "❌ Skip", "❌ Пропуск")
    fun btnDelete(l: Lang) = pick(l, "🗑 Delete", "🗑 Удалить")

    fun cbBadButton(l: Lang) = pick(l, "Bad button", "Неверная кнопка")
    fun cbError(l: Lang) = pick(l, "Error", "Ошибка")
    fun cbBadDate(l: Lang) = pick(l, "Bad date", "Неверная дата")
    fun cbDeleted(l: Lang) = pick(l, "Deleted 🗑", "Удалено 🗑")
    fun cbNotFound(l: Lang) = pick(l, "Not found", "Не найдено")
    fun cbDone(l: Lang) = pick(l, "Done ✅", "Готово ✅")
    fun cbSkipped(l: Lang) = pick(l, "Skipped ❌", "Пропущено ❌")
    fun cbRemovedShort(l: Lang) = pick(l, "Removed", "Удалено")
    fun cbRemovedFull(l: Lang) = pick(l, "Habit removed.", "Привычка удалена.")
    fun cbPausedShort(l: Lang) = pick(l, "Paused", "На паузе")
    fun cbPausedFull(l: Lang) = pick(l, "Habit paused.", "Привычка на паузе.")
    fun cbResumedShort(l: Lang) = pick(l, "Resumed", "Возобновлено")
    fun cbResumedFull(l: Lang) = pick(l, "Habit resumed.", "Привычка возобновлена.")

    private fun pick(l: Lang, en: String, ru: String): String = if (l == Lang.RU) ru else en
}

object BotProfileI18n {
    fun name(l: Lang): String = when (l) {
        Lang.EN -> "Habit Tracker"
        Lang.RU -> "Трекер привычек"
    }

    fun description(l: Lang): String = when (l) {
        Lang.EN -> """
            Habit tracker bot. Set up daily habits, get reminders at the times you choose, and mark each one done or skipped.

            Tap /start to begin or /addhabit to add your first habit.
        """.trimIndent()
        Lang.RU -> """
            Бот для трекинга привычек. Заведите ежедневные привычки, получайте напоминания в нужное время и отмечайте «готово» или «пропуск».

            Нажмите /start, чтобы начать, или /addhabit, чтобы добавить первую привычку.
        """.trimIndent()
    }

    fun shortDescription(l: Lang): String = when (l) {
        Lang.EN -> "Track daily habits with reminders, check-ins and streaks."
        Lang.RU -> "Трекинг ежедневных привычек: напоминания, чек-ины и серии."
    }
}

object BotCommandsI18n {
    fun list(l: Lang): List<Pair<String, String>> = when (l) {
        Lang.EN -> listOf(
            "start" to "show help",
            "addhabit" to "add a habit (interactive)",
            "habits" to "list habits",
            "removehabit" to "remove a habit",
            "pause" to "pause a habit",
            "resume" to "resume a paused habit",
            "checkin" to "today's check-ins",
            "stats" to "statistics",
            "tz" to "show or set your timezone",
            "lang" to "switch language (en/ru)",
        )
        Lang.RU -> listOf(
            "start" to "помощь",
            "addhabit" to "добавить привычку (интерактивно)",
            "habits" to "список привычек",
            "removehabit" to "удалить привычку",
            "pause" to "поставить привычку на паузу",
            "resume" to "возобновить привычку",
            "checkin" to "чек-ины за сегодня",
            "stats" to "статистика",
            "tz" to "показать или задать часовой пояс",
            "lang" to "сменить язык (en/ru)",
        )
    }
}
