import dev.inmo.tgbotapi.types.abstracts.WithOptionalLanguageCode
import dev.inmo.tgbotapi.types.chat.User
import dto.Direction
import dto.Habit
import dto.HabitStat
import dto.HabitType
import dto.HabitWeekStat
import java.time.LocalDate

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

    fun pickHabitType(l: Lang) = pick(l,
        "Pick a habit type:\n• scheduled — fixed reminder times, done/skip\n• counter — count check-ins (optional daily target, optional direction)\n• quantity — log decimal amounts (optional target, unit, direction)",
        "Выберите тип привычки:\n• расписание — фиксированные напоминания, готово/пропуск\n• счётчик — считать чек-ины (опциональные цель и направление)\n• количество — вводить вещественные значения (опциональные цель, единица, направление)")

    fun typeButtonLabel(l: Lang, t: HabitType): String = when (t) {
        HabitType.SCHEDULED -> pick(l, "📅 scheduled", "📅 расписание")
        HabitType.COUNTER -> pick(l, "🔢 counter", "🔢 счётчик")
        HabitType.QUANTITY -> pick(l, "⚖️ quantity", "⚖️ количество")
    }

    fun directionButtonLabel(l: Lang, d: Direction?): String = when (d) {
        Direction.MORE -> pick(l, "⬆ more is better", "⬆ больше — лучше")
        Direction.LESS -> pick(l, "⬇ less is better", "⬇ меньше — лучше")
        null -> pick(l, "— no direction", "— без направления")
    }

    fun sendDailyTarget(l: Lang) = pick(l,
        "Daily target (integer, e.g. 5)? Send \"-\" to skip.",
        "Дневная цель (целое число, например 5)? Отправьте «-», чтобы пропустить.")

    fun invalidTarget(l: Lang) = pick(l,
        "Target must be a positive integer or \"-\".",
        "Цель должна быть положительным целым или «-».")

    fun sendDailyTargetValue(l: Lang) = pick(l,
        "Daily target (decimal, e.g. 1.5)? Send \"-\" to skip.",
        "Дневная цель (вещественное, например 1.5)? Отправьте «-», чтобы пропустить.")

    fun invalidTargetValue(l: Lang) = pick(l,
        "Target must be a positive number or \"-\".",
        "Цель должна быть положительным числом или «-».")

    fun sendUnit(l: Lang) = pick(l,
        "Unit (e.g. km, kg, ml)? Send \"-\" to skip.",
        "Единица измерения (например км, кг, мл)? Отправьте «-», чтобы пропустить.")

    fun sendDirection(l: Lang) = pick(l,
        "Direction:",
        "Направление:")

    fun sendFirstReminderTime(l: Lang) = pick(l,
        "Send a reminder time (HH:MM), e.g. 09:00:",
        "Отправьте время напоминания (ЧЧ:ММ), например 09:00:")

    fun sendFirstReminderTimeOptional(l: Lang) = pick(l,
        "Send a reminder time (HH:MM), or \"-\" if you don't need reminders:",
        "Отправьте время напоминания (ЧЧ:ММ) или «-», если напоминания не нужны:")

    fun sendNextReminderTimeOrDone(l: Lang) = pick(l,
        "Add another reminder time (HH:MM), or \"done\" to finish:",
        "Добавьте ещё одно время напоминания (ЧЧ:ММ) или «готово», чтобы закончить:")

    fun duplicateTime(l: Lang) = pick(l,
        "That time is already added. Send a different one.",
        "Это время уже добавлено. Отправьте другое.")

    fun invalidTime(l: Lang) = pick(l,
        "Invalid time format. Use HH:MM, e.g. 09:00.",
        "Неверный формат времени. Используйте ЧЧ:ММ, например 09:00.")

    fun sendReminderDaysFor(l: Lang, time: String) = pick(l,
        "Which weekdays should the $time reminder fire? Send numbers 1-7 (1=Mon … 7=Sun), space-separated. Example: 1 2 3 4 5. Send \"-\" for every day.",
        "В какие дни недели слать напоминание в $time? Отправьте числа 1-7 (1=Пн … 7=Вс) через пробел. Пример: 1 2 3 4 5. Отправьте «-» — каждый день.")

    fun invalidDays(l: Lang) = pick(l,
        "Invalid days. Use numbers 1-7 (1=Mon … 7=Sun), e.g. 1 3 5.",
        "Неверные дни. Используйте числа 1-7 (1=Пн … 7=Вс), например 1 3 5.")

    /** Formats ISO weekday numbers (1=Mon..7=Sun) as short localized names. Empty = every day. */
    fun formatDays(l: Lang, days: List<Int>): String {
        if (days.isEmpty()) return ""
        val names = if (l == Lang.RU) listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
                    else listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        return days.filter { it in 1..7 }.sorted().joinToString(",") { names[it - 1] }
    }

    fun logBadge(l: Lang): String = pick(l, " · 📒 log", " · 📒 журнал")

    fun habitAddedDetailed(l: Lang, h: Habit): String {
        val type = habitTypeLabel(l, h) + if (h.logOnly) logBadge(l) else ""
        val times = h.reminders.joinToString(", ") { rem ->
            val d = if (rem.days.isNotEmpty()) " (${formatDays(l, rem.days)})" else ""
            "${rem.time.format(Keyboards.TIME_FMT)}$d"
        }
        if (h.isGroupRoot) {
            val header = pick(l, "Added: \"${h.name}\" [$type]", "Добавлено: «${h.name}» [$type]")
            val fieldLines = h.fields.map { f ->
                val unit = f.unit?.let { " $it" } ?: ""
                val target = f.dailyTarget?.let { " — ${formatAmount(it)}$unit/day" } ?: ""
                val dir = f.direction?.let { " (${directionLabel(l, it)})" } ?: ""
                "  – ${f.name}$target$dir"
            }
            val timesLine = if (times.isNotEmpty()) listOf("  ⏰ $times") else emptyList()
            return (listOf(header) + fieldLines + timesLine).joinToString("\n")
        }
        val tail = buildString {
            when (h.type) {
                HabitType.SCHEDULED -> append(" — $times")
                HabitType.COUNTER -> {
                    h.dailyTarget?.toInt()?.let { append(" — ${pick(l, "target: $it/day", "цель: $it/день")}") }
                    h.direction?.let { append(" — ${directionLabel(l, it)}") }
                    if (times.isNotEmpty()) append(" — $times")
                }
                HabitType.QUANTITY -> {
                    h.dailyTarget?.let {
                        val unit = h.unit?.let { u -> " $u" } ?: ""
                        append(" — ${pick(l, "target: ${formatAmount(it)}$unit/day", "цель: ${formatAmount(it)}$unit/день")}")
                    }
                    if (h.dailyTarget == null) {
                        h.unit?.let { append(" — ${pick(l, "unit: $it", "ед.: $it")}") }
                    }
                    h.direction?.let { append(" — ${directionLabel(l, it)}") }
                    if (times.isNotEmpty()) append(" — $times")
                }
            }
        }
        return pick(l, "Added: \"${h.name}\" [$type]$tail", "Добавлено: «${h.name}» [$type]$tail")
    }

    fun noHabits(l: Lang) = pick(l,
        "No habits yet. Add one with /addhabit.",
        "Привычек ещё нет. Добавьте через /addhabit.")

    fun yourHabits(l: Lang) = pick(l, "Your habits:", "Ваши привычки:")

    fun noReminders(l: Lang) = pick(l, "no reminders", "без напоминаний")

    fun nothingToRemove(l: Lang) = pick(l, "Nothing to remove.", "Удалять нечего.")

    fun pickHabitToRemove(l: Lang) = pick(l, "Pick a habit to remove:", "Выберите привычку для удаления:")

    fun noActiveToPause(l: Lang) = pick(l, "No active habits to pause.", "Нет активных привычек для паузы.")

    fun pickHabitToPause(l: Lang) = pick(l, "Pick a habit to pause:", "Выберите привычку для паузы:")

    fun noPaused(l: Lang) = pick(l, "No paused habits.", "Нет привычек на паузе.")

    fun pickHabitToResume(l: Lang) = pick(l, "Pick a habit to resume:", "Выберите привычку для возобновления:")

    fun nothingToCheckIn(l: Lang) = pick(l, "Nothing to check in.", "Чек-инить нечего.")

    fun pendingCheckIns(l: Lang) = pick(l, "Pending check-ins:", "Ожидают чек-ина:")

    fun counterLine(l: Lang, h: Habit, current: Int, date: LocalDate): String {
        val target = h.dailyTarget?.toInt()
        val mark = when {
            target != null && current >= target -> "✅"
            target != null -> "⏳"
            else -> "•"
        }
        val body = buildString {
            append(if (target != null) "$current/$target" else "$current")
            h.direction?.let { append(" ${directionShort(l, it)}") }
        }
        return "$mark $date — ${h.name}: $body"
    }

    fun habitTypeLabel(l: Lang, h: Habit): String = when (h.type) {
        HabitType.SCHEDULED -> pick(l, "scheduled", "расписание")
        HabitType.COUNTER -> pick(l, "counter", "счётчик")
        HabitType.QUANTITY -> pick(l, "quantity", "количество")
    }

    fun formatAmount(v: Double): String {
        if (v.isNaN() || v.isInfinite()) return "0"
        val rounded = Math.round(v * 1000.0) / 1000.0
        return if (rounded % 1.0 == 0.0) rounded.toLong().toString()
               else String.format(java.util.Locale.ROOT, "%.3f", rounded).trimEnd('0').trimEnd('.')
    }

    fun directionLabel(l: Lang, d: Direction?): String = when (d) {
        Direction.MORE -> pick(l, "more is better", "больше — лучше")
        Direction.LESS -> pick(l, "less is better", "меньше — лучше")
        null -> pick(l, "no direction", "без направления")
    }

    private fun directionShort(l: Lang, d: Direction): String = when (d) {
        Direction.MORE -> pick(l, "more↑", "больше↑")
        Direction.LESS -> pick(l, "less↓", "меньше↓")
    }

    fun noStats(l: Lang) = pick(l, "No habits to report on.", "Не по чем статистику показывать.")

    fun statsHeader(l: Lang) = pick(l, "Stats:", "Статистика:")

    fun statsCompletion(l: Lang) = pick(l, "completion", "выполнение")

    fun statsStreak(l: Lang, days: Int) = pick(l,
        "🔥 streak: $days day(s)",
        "🔥 серия: $days дн.")

    fun statsLines(l: Lang, s: HabitStat): List<String> {
        val pct = if (s.totalDays > 0) "%.0f%%".format(java.util.Locale.ROOT, s.loggedDays * 100.0 / s.totalDays) else "—"
        val days = pick(l,
            "📅 ${s.loggedDays}/${s.totalDays} days ($pct)",
            "📅 ${s.loggedDays}/${s.totalDays} дн. ($pct)")
        val lines = mutableListOf("${statsStreak(l, s.streak)}   $days")
        s.trend?.let { lines += trendLine(l, it) }
        s.groupFields.forEach { f ->
            f.trend?.let { lines += "${f.name}: ${trendLine(l, it)}" }
        }
        return lines
    }

    private fun trendLine(l: Lang, t: dto.QuantityTrend): String {
        val unit = t.unit?.let { " $it" } ?: ""
        val today = formatAmount(t.today)
        val recent = formatAmount(t.recentAvg)
        val overall = formatAmount(t.overallAvg)
        val delta = t.recentAvg - t.overallAvg
        val arrow = when {
            delta > 0.0 -> "↑"
            delta < 0.0 -> "↓"
            else -> "→"
        }
        val verdict = when {
            t.direction == null || delta == 0.0 -> ""
            (t.direction == Direction.MORE && delta > 0.0) ||
            (t.direction == Direction.LESS && delta < 0.0) -> " ✅"
            else -> " ⚠️"
        }
        return pick(l,
            "📈 $today$unit · ${t.windowDays}d $recent · all $overall · $arrow$verdict",
            "📈 $today$unit · ${t.windowDays}д $recent · всё $overall · $arrow$verdict")
    }

    fun weeklySummary(
        l: Lang,
        from: LocalDate,
        to: LocalDate,
        stats: List<HabitWeekStat>
    ): String? {
        if (stats.isEmpty()) return null
        val activity = stats.any { s ->
            s.scheduledDone + s.scheduledSkip + s.counterTotal > 0 || s.quantityTotal > 0.0
        }
        if (!activity) return null

        return buildString {
            appendLine(pick(l,
                "📊 Past week ($from – $to):",
                "📊 Прошлая неделя ($from – $to):"))
            stats.forEach { s ->
                appendLine("• ${s.name}")
                when (s.type) {
                    HabitType.SCHEDULED -> {
                        val total = s.scheduledDone + s.scheduledSkip
                        val rate = if (total > 0) "%.0f%%".format(java.util.Locale.ROOT, s.scheduledDone * 100.0 / total) else "—"
                        appendLine("    ✅ ${s.scheduledDone}   ❌ ${s.scheduledSkip}   ${statsCompletion(l)}: $rate")
                    }
                    HabitType.COUNTER -> {
                        val avg = if (s.counterDays > 0) "%.1f".format(java.util.Locale.ROOT, s.counterTotal.toDouble() / s.counterDays) else "0"
                        val dirSuffix = s.direction?.let { "   (${directionShort(l, it)})" } ?: ""
                        appendLine(pick(l,
                            "    total: ${s.counterTotal}   days: ${s.counterDays}   avg/day: $avg$dirSuffix",
                            "    всего: ${s.counterTotal}   дней: ${s.counterDays}   среднее: $avg$dirSuffix"))
                        val target = s.dailyTarget
                        if (target != null) {
                            appendLine(pick(l,
                                "    🎯 target hit: ${s.targetHitDays}/7",
                                "    🎯 цель достигнута: ${s.targetHitDays}/7"))
                        }
                    }
                    HabitType.QUANTITY -> {
                        val unit = s.unit?.let { " $it" } ?: ""
                        val avg = if (s.quantityDays > 0) formatAmount(s.quantityTotal / s.quantityDays) else "0"
                        val dirSuffix = s.direction?.let { "   (${directionShort(l, it)})" } ?: ""
                        appendLine(pick(l,
                            "    total: ${formatAmount(s.quantityTotal)}$unit   days: ${s.quantityDays}   avg/day: $avg$unit$dirSuffix",
                            "    всего: ${formatAmount(s.quantityTotal)}$unit   дней: ${s.quantityDays}   среднее: $avg$unit$dirSuffix"))
                        if (s.dailyTarget != null) {
                            appendLine(pick(l,
                                "    🎯 target hit: ${s.targetHitDays}/7",
                                "    🎯 цель достигнута: ${s.targetHitDays}/7"))
                        }
                    }
                }
            }
        }.trimEnd()
    }

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

    fun mcpTokenIssuedPrefix(l: Lang, label: String, url: String) = pick(l,
        "MCP token issued for \"$label\". Use this header in your MCP client (URL: $url):\nAuthorization: Bearer",
        "MCP-токен выдан для «$label». Используйте этот заголовок в MCP-клиенте (URL: $url):\nAuthorization: Bearer")

    fun mcpTokenIssuedSuffix(l: Lang) = pick(l,
        "Save it now — it won't be shown again. Manage with /mcp_list, /mcp_revoke <id>.",
        "Сохраните прямо сейчас — снова показан не будет. Управление: /mcp_list, /mcp_revoke <id>.")

    fun mcpTokenLimitReached(l: Lang, max: Int) = pick(l,
        "Active token limit reached ($max). Revoke one with /mcp_revoke <id>.",
        "Достигнут лимит активных токенов ($max). Отзовите один: /mcp_revoke <id>.")

    fun mcpNoTokens(l: Lang) = pick(l,
        "No active MCP tokens. Create one with /mcp_new [label].",
        "Активных MCP-токенов нет. Создайте через /mcp_new [метка].")

    fun mcpTokensHeader(l: Lang) = pick(l, "Active MCP tokens:", "Активные MCP-токены:")

    fun mcpCreatedAt(l: Lang) = pick(l, "created", "создан")

    fun mcpLastUsed(l: Lang) = pick(l, "last used", "последнее использование")

    fun mcpNeverUsed(l: Lang) = pick(l, "never", "не использовался")

    fun mcpRevokeUsage(l: Lang) = pick(l,
        "Usage: /mcp_revoke <id>  (see /mcp_list for ids)",
        "Использование: /mcp_revoke <id>  (id см. в /mcp_list)")

    fun mcpTokenRevoked(l: Lang, id: Long) = pick(l,
        "Token #$id revoked.",
        "Токен #$id отозван.")

    fun mcpTokenNotFound(l: Lang, id: Long) = pick(l,
        "Token #$id not found or already revoked.",
        "Токен #$id не найден или уже отозван.")

    fun mcpRecordedQuantity(l: Lang, name: String, amount: Double, unit: String?, date: LocalDate, comment: String?): String {
        val u = unit?.let { " $it" } ?: ""
        val c = comment?.let { "\n💬 $it" } ?: ""
        return pick(l,
            "🤖 via MCP — $name: ${formatAmount(amount)}$u — $date$c",
            "🤖 через MCP — $name: ${formatAmount(amount)}$u — $date$c")
    }

    fun mcpRecordedQuantityGroup(l: Lang, root: Habit, perField: Map<Long, Double>, date: LocalDate, comment: String?): String {
        val header = pick(l,
            "🤖 via MCP — ${root.name} — $date",
            "🤖 через MCP — ${root.name} — $date")
        val fieldLines = root.fields.mapNotNull { f ->
            val v = perField[f.id] ?: return@mapNotNull null
            val u = f.unit?.let { " $it" } ?: ""
            "  – ${f.name}: ${formatAmount(v)}$u"
        }
        val body = (listOf(header) + fieldLines).joinToString("\n")
        return comment?.let { "$body\n💬 $it" } ?: body
    }

    fun btnDone(l: Lang) = pick(l, "✅ Done", "✅ Готово")
    fun btnSkip(l: Lang) = pick(l, "❌ Skip", "❌ Пропуск")
    fun btnDelete(l: Lang) = pick(l, "🗑 Delete", "🗑 Удалить")
    fun btnPlusOne(l: Lang) = pick(l, "➕1", "➕1")
    fun btnModeSingle(l: Lang) = pick(l, "📏 single value", "📏 одно значение")
    fun btnModeGroup(l: Lang) = pick(l, "🧩 multiple fields", "🧩 несколько полей")

    fun pickLogMode(l: Lang) = pick(l,
        "Track metrics, or just keep a log?\n• tracked — streaks, completion, trends and weekly summary\n• log only — just a journal of entries, no targets/streaks, hidden from /stats",
        "Считать метрики или просто вести журнал?\n• с метриками — серии, выполнение, тренды и недельная сводка\n• только журнал — лог записей без целей/серий, скрыт из /stats")

    fun btnTracked(l: Lang) = pick(l, "📊 tracked", "📊 с метриками")
    fun btnLogOnly(l: Lang) = pick(l, "📒 log only", "📒 только журнал")

    fun pickQuantityMode(l: Lang) = pick(l,
        "Single value (one number per check-in) or multiple fields (e.g. distance + duration + calories)?",
        "Одно значение (одно число на чек-ин) или несколько полей (напр. дистанция + время + ккал)?")

    fun sendFirstFieldName(l: Lang) = pick(l,
        "Name of the first field (e.g. \"km\"):",
        "Название первого поля (например, «км»):")

    fun sendNextFieldNameOrDone(l: Lang) = pick(l,
        "Next field name, or \"done\" to finish:",
        "Название следующего поля или «готово», чтобы закончить:")

    fun cbBadButton(l: Lang) = pick(l, "Bad button", "Неверная кнопка")
    fun cbError(l: Lang) = pick(l, "Error", "Ошибка")
    fun cbBadDate(l: Lang) = pick(l, "Bad date", "Неверная дата")
    fun cbDeleted(l: Lang) = pick(l, "Deleted 🗑", "Удалено 🗑")
    fun cbNotFound(l: Lang) = pick(l, "Not found", "Не найдено")
    fun cbDone(l: Lang) = pick(l, "Done ✅", "Готово ✅")
    fun cbSkipped(l: Lang) = pick(l, "Skipped ❌", "Пропущено ❌")
    fun cbLogged(l: Lang) = pick(l, "Logged ➕", "Записано ➕")
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
            "checkin" to "today's check-ins",
            "stats" to "statistics",
            "habits" to "list habits",
            "addhabit" to "add a habit (interactive)",
            "pause" to "pause a habit",
            "resume" to "resume a paused habit",
            "removehabit" to "remove a habit",
            "tz" to "show or set your timezone",
            "lang" to "switch language (en/ru)",
            "mcp_new" to "create an MCP API token",
            "mcp_list" to "list MCP API tokens",
            "mcp_revoke" to "revoke an MCP API token",
        )
        Lang.RU -> listOf(
            "checkin" to "чек-ины за сегодня",
            "stats" to "статистика",
            "habits" to "список привычек",
            "addhabit" to "добавить привычку (интерактивно)",
            "pause" to "поставить привычку на паузу",
            "resume" to "возобновить привычку",
            "removehabit" to "удалить привычку",
            "tz" to "показать или задать часовой пояс",
            "lang" to "сменить язык (en/ru)",
            "mcp_new" to "создать токен MCP API",
            "mcp_list" to "список токенов MCP API",
            "mcp_revoke" to "отозвать токен MCP API",
        )
    }
}
