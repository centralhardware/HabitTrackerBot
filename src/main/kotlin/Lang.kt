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

    fun sendAmount(l: Lang, h: Habit) = pick(l,
        "Send the amount for \"${h.name}\"${h.unit?.let { " ($it)" } ?: ""} — optional comment after a space:",
        "Отправьте количество для «${h.name}»${h.unit?.let { " ($it)" } ?: ""} — после пробела можно добавить комментарий:")

    fun invalidAmount(l: Lang) = pick(l,
        "Amount must be a positive number.",
        "Количество должно быть положительным числом.")

    fun sendDirection(l: Lang) = pick(l,
        "Direction:",
        "Направление:")

    fun sendTimes(l: Lang) = pick(l,
        "Send one or more reminder times (HH:MM), space-separated. Example: 09:00 21:00",
        "Отправьте одно или несколько времён напоминаний (ЧЧ:ММ) через пробел. Пример: 09:00 21:00")

    fun sendOptionalTimes(l: Lang) = pick(l,
        "Reminder times (HH:MM, space-separated) — or \"-\" for none.",
        "Времена напоминаний (ЧЧ:ММ через пробел) или «-», если не нужны.")

    fun invalidTime(l: Lang) = pick(l,
        "Invalid time format. Use HH:MM, e.g. 09:00.",
        "Неверный формат времени. Используйте ЧЧ:ММ, например 09:00.")

    fun noTimes(l: Lang) = pick(l, "No times provided.", "Время не указано.")

    fun habitAddedDetailed(l: Lang, h: Habit): String {
        val type = habitTypeLabel(l, h)
        val times = h.reminders.joinToString(", ") { it.format(Keyboards.TIME_FMT) }
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

    fun quantityLine(l: Lang, h: Habit, current: Double, date: LocalDate): String {
        val target = h.dailyTarget
        val unit = h.unit?.let { " $it" } ?: ""
        val hitOk = when (h.direction) {
            Direction.LESS -> target != null && current <= target
            else -> target != null && current >= target
        }
        val mark = when {
            target != null && hitOk -> "✅"
            target != null -> "⏳"
            else -> "•"
        }
        val body = buildString {
            append(if (target != null) "${formatAmount(current)}/${formatAmount(target)}$unit" else "${formatAmount(current)}$unit")
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

    fun statsCounterTarget(l: Lang, s: HabitStat.Counter.WithTarget): List<String> {
        val total = s.doneDays + s.skipDays
        val rate = if (total > 0) "%.0f%%".format(s.doneDays * 100.0 / total) else "—"
        val todayMark = run {
            val ok = s.direction == Direction.LESS && s.todayCount <= s.dailyTarget ||
                     s.direction != Direction.LESS && s.todayCount >= s.dailyTarget
            if (ok) "✅" else "⏳"
        }
        val dirSuffix = s.direction?.let { "   (${directionShort(l, it)})" } ?: ""
        val first = pick(l,
            "✅ ${s.doneDays}   ❌ ${s.skipDays}   ${statsCompletion(l)}: $rate$dirSuffix",
            "✅ ${s.doneDays}   ❌ ${s.skipDays}   ${statsCompletion(l)}: $rate$dirSuffix")
        val second = pick(l,
            "$todayMark today: ${s.todayCount}/${s.dailyTarget}   ${statsStreak(l, s.streak)}",
            "$todayMark сегодня: ${s.todayCount}/${s.dailyTarget}   ${statsStreak(l, s.streak)}")
        return listOf(first, second)
    }

    fun statsCounterTrend(l: Lang, s: HabitStat.Counter.Trend): List<String> {
        val dir = directionShort(l, s.direction)
        val avgFmt = "%.1f".format(s.overallAvg)
        val header = pick(l,
            "today: ${s.todayCount}   yest: ${s.yesterdayCount}   total: ${s.grandTotal}   days: ${s.daysLogged}   avg/day: $avgFmt   ($dir)",
            "сегодня: ${s.todayCount}   вчера: ${s.yesterdayCount}   всего: ${s.grandTotal}   дней: ${s.daysLogged}   среднее: $avgFmt   ($dir)")

        val lines = mutableListOf(header)
        trendComparisonLine(l, s.direction,
            labelEn = "today vs yest",
            labelRu = "сегодня vs вчера",
            recent = s.todayCount.toDouble(),
            previous = s.yesterdayCount.toDouble(),
            asInt = true
        )?.let { lines += it }

        trendComparisonLine(l, s.direction,
            labelEn = "3d",
            labelRu = "3д",
            recent = s.recent3Avg,
            previous = s.previous3Avg,
            asInt = false
        )?.let { lines += it }

        trendComparisonLine(l, s.direction,
            labelEn = "7d",
            labelRu = "7д",
            recent = s.recent7Avg,
            previous = s.previous7Avg,
            asInt = false
        )?.let { lines += it }

        return lines
    }

    private fun trendComparisonLine(
        l: Lang,
        direction: Direction,
        labelEn: String,
        labelRu: String,
        recent: Double,
        previous: Double,
        asInt: Boolean
    ): String? {
        if (recent == 0.0 && previous == 0.0) return null
        val delta = recent - previous
        val pct = if (previous > 0.0) "%+.0f%%".format(delta / previous * 100.0) else "—"
        val arrow = when {
            delta > 0.0 -> "↑"
            delta < 0.0 -> "↓"
            else -> "→"
        }
        val verdict = when {
            delta == 0.0 -> "→"
            (direction == Direction.MORE && delta > 0.0) ||
            (direction == Direction.LESS && delta < 0.0) -> "✅"
            else -> "⚠️"
        }
        val r = if (asInt) recent.toInt().toString() else "%.1f".format(recent)
        val p = if (asInt) previous.toInt().toString() else "%.1f".format(previous)
        return pick(l,
            "$arrow $labelEn: $r vs $p   $pct $verdict",
            "$arrow $labelRu: $r против $p   $pct $verdict")
    }

    fun statsCounterPlain(l: Lang, s: HabitStat.Counter.Plain): String =
        pick(l,
            "today: ${s.todayCount}   total: ${s.grandTotal}   days: ${s.daysLogged}",
            "сегодня: ${s.todayCount}   всего: ${s.grandTotal}   дней: ${s.daysLogged}")

    fun statsQuantityTarget(l: Lang, s: HabitStat.Quantity.WithTarget): List<String> {
        val total = s.doneDays + s.skipDays
        val rate = if (total > 0) "%.0f%%".format(java.util.Locale.ROOT, s.doneDays * 100.0 / total) else "—"
        val todayMark = run {
            val ok = s.direction == Direction.LESS && s.todayTotal <= s.dailyTarget ||
                     s.direction != Direction.LESS && s.todayTotal >= s.dailyTarget
            if (ok) "✅" else "⏳"
        }
        val dirSuffix = s.direction?.let { "   (${directionShort(l, it)})" } ?: ""
        val unit = s.unit?.let { " $it" } ?: ""
        val today = formatAmount(s.todayTotal)
        val target = formatAmount(s.dailyTarget)
        val first = pick(l,
            "✅ ${s.doneDays}   ❌ ${s.skipDays}   ${statsCompletion(l)}: $rate$dirSuffix",
            "✅ ${s.doneDays}   ❌ ${s.skipDays}   ${statsCompletion(l)}: $rate$dirSuffix")
        val second = pick(l,
            "$todayMark today: $today/$target$unit   ${statsStreak(l, s.streak)}",
            "$todayMark сегодня: $today/$target$unit   ${statsStreak(l, s.streak)}")
        return listOf(first, second)
    }

    fun statsQuantityTrend(l: Lang, s: HabitStat.Quantity.Trend): List<String> {
        val dir = directionShort(l, s.direction)
        val unit = s.unit?.let { " $it" } ?: ""
        val avgFmt = formatAmount(s.overallAvg)
        val header = pick(l,
            "today: ${formatAmount(s.todayTotal)}$unit   yest: ${formatAmount(s.yesterdayTotal)}$unit   total: ${formatAmount(s.grandTotal)}$unit   days: ${s.daysLogged}   avg/day: $avgFmt$unit   ($dir)",
            "сегодня: ${formatAmount(s.todayTotal)}$unit   вчера: ${formatAmount(s.yesterdayTotal)}$unit   всего: ${formatAmount(s.grandTotal)}$unit   дней: ${s.daysLogged}   среднее: $avgFmt$unit   ($dir)")

        val lines = mutableListOf(header)
        trendComparisonLine(l, s.direction, "today vs yest", "сегодня vs вчера", s.todayTotal, s.yesterdayTotal, asInt = false)?.let { lines += it }
        trendComparisonLine(l, s.direction, "3d", "3д", s.recent3Avg, s.previous3Avg, asInt = false)?.let { lines += it }
        trendComparisonLine(l, s.direction, "7d", "7д", s.recent7Avg, s.previous7Avg, asInt = false)?.let { lines += it }
        return lines
    }

    fun statsQuantityPlain(l: Lang, s: HabitStat.Quantity.Plain): String {
        val unit = s.unit?.let { " $it" } ?: ""
        return pick(l,
            "today: ${formatAmount(s.todayTotal)}$unit   total: ${formatAmount(s.grandTotal)}$unit   days: ${s.daysLogged}",
            "сегодня: ${formatAmount(s.todayTotal)}$unit   всего: ${formatAmount(s.grandTotal)}$unit   дней: ${s.daysLogged}")
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

    fun btnDone(l: Lang) = pick(l, "✅ Done", "✅ Готово")
    fun btnSkip(l: Lang) = pick(l, "❌ Skip", "❌ Пропуск")
    fun btnDelete(l: Lang) = pick(l, "🗑 Delete", "🗑 Удалить")
    fun btnPlusOne(l: Lang) = pick(l, "➕1", "➕1")
    fun btnLog(l: Lang) = pick(l, "➕ log", "➕ ввести")

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
            "mcp_new" to "create an MCP API token",
            "mcp_list" to "list MCP API tokens",
            "mcp_revoke" to "revoke an MCP API token",
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
            "mcp_new" to "создать токен MCP API",
            "mcp_list" to "список токенов MCP API",
            "mcp_revoke" to "отозвать токен MCP API",
        )
    }
}
