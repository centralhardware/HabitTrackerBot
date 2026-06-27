import dev.inmo.tgbotapi.types.abstracts.WithOptionalLanguageCode
import dev.inmo.tgbotapi.types.chat.User
import dto.CheckinStatus
import dto.Direction
import dto.FieldValue
import dto.RecentCheckin
import dto.Track
import dto.TrackParam
import dto.TrackStat
import dto.TrackType
import dto.TrackWeekStat
import dto.ParamType
import java.time.LocalDate
import kotlin.math.roundToLong

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

        /** Parses a stored language code (an enum name); null/unknown yields null. */
        fun stored(code: String?): Lang? = code?.let { runCatching { valueOf(it) }.getOrNull() }
    }
}

object Strings {

    fun startHelp(l: Lang): String = when (l) {
        Lang.EN -> """
            TrackAll — track anything: habits, metrics, time.

            Commands:
            /addtrack — add a track (interactive)
            /cancel — cancel the current /addtrack dialog
            /tracks — list active tracks
            /removetrack — remove a track
            /pause — pause reminders for a track
            /resume — resume a paused track
            /checkin — today's check-ins
            /log — recent check-ins (paged)
            /timer — start/stop time tracking
            /stats — statistics
            /tz — show or set your timezone (e.g. /tz Europe/Moscow)
            /lang — switch language (en/ru)
        """.trimIndent()
        Lang.RU -> """
            TrackAll — трекай что угодно: привычки, показатели, время.

            Команды:
            /addtrack — добавить трек (интерактивно)
            /cancel — отменить текущий диалог /addtrack
            /tracks — список активных треков
            /removetrack — удалить трек
            /pause — поставить напоминания на паузу
            /resume — возобновить трек
            /checkin — чек-ины за сегодня
            /log — последние чек-ины (постранично)
            /timer — старт/стоп отслеживания времени
            /stats — статистика
            /tz — показать или задать часовой пояс (например, /tz Europe/Moscow)
            /lang — сменить язык (en/ru)
        """.trimIndent()
    }

    fun tzRequiredAddTrack(l: Lang) = pick(l,
        "Set your timezone first with /tz <IANA name>, e.g. /tz Europe/Moscow",
        "Сначала задайте часовой пояс через /tz <IANA>, например /tz Europe/Moscow")

    fun tzRequiredCheckIn(l: Lang) = pick(l,
        "Set your timezone first with /tz <IANA name>.",
        "Сначала задайте часовой пояс через /tz <IANA>.")

    fun sendTrackName(l: Lang) = pick(l,
        "Send the track name:",
        "Отправьте название трека:")

    fun cancelled(l: Lang) = pick(l, "Cancelled.", "Отменено.")

    fun pickTrackType(l: Lang) = pick(l,
        "Pick a track type:\n• check — done/skip track: mark scheduled times and/or log check-ins any time\n• quantity — log decimal amounts (optional target, unit, direction)\n• timer — auto-track time spent (start/stop)",
        "Выберите тип трека:\n• отметка — трек готово/пропуск: отмечать по расписанию и/или в любое время\n• количество — вводить вещественные значения (опциональные цель, единица, направление)\n• таймер — автоматически засекать время (старт/стоп)")

    fun typeButtonLabel(l: Lang, t: TrackType): String = when (t) {
        TrackType.CHECK -> pick(l, "✅ check", "✅ отметка")
        TrackType.QUANTITY -> pick(l, "⚖️ quantity", "⚖️ количество")
        TrackType.TIMER -> pick(l, "⏱ timer", "⏱ таймер")
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

    fun sendTimerTarget(l: Lang) = pick(l,
        "Daily target in minutes (integer, e.g. 60)? Send \"-\" to skip.",
        "Дневная цель в минутах (целое число, например 60)? Отправьте «-», чтобы пропустить.")

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
        "Send a reminder time (HH:MM, 0–47h). Times ≥ 24:00 fire on the next calendar day, e.g. 25:30 = 01:30 next day:",
        "Отправьте время напоминания (ЧЧ:ММ, 0–47ч). Часы ≥ 24 означают следующий день, например 25:30 = 01:30 следующего дня:")

    fun sendFirstReminderTimeOptional(l: Lang) = pick(l,
        "Send a reminder time (HH:MM, 0–47h), or \"-\" if you don't need reminders:",
        "Отправьте время напоминания (ЧЧ:ММ, 0–47ч) или «-», если напоминания не нужны:")

    fun sendNextReminderTimeOrDone(l: Lang) = pick(l,
        "Add another reminder time (HH:MM, 0–47h), or \"done\" to finish:",
        "Добавьте ещё одно время напоминания (ЧЧ:ММ, 0–47ч) или «готово», чтобы закончить:")

    fun duplicateTime(l: Lang) = pick(l,
        "That time is already added. Send a different one.",
        "Это время уже добавлено. Отправьте другое.")

    fun invalidTime(l: Lang) = pick(l,
        "Invalid time format. Use HH:MM with hours 0–47, e.g. 09:00 or 25:30.",
        "Неверный формат времени. Используйте ЧЧ:ММ, часы 0–47, например 09:00 или 25:30.")

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

    /**
     * Formats a reminder offset (minutes since midnight, 0–2879) as HH:MM, keeping the raw hour
     * (e.g. 25:00). Offsets ≥ 24:00 fire on the next calendar day, so a short "+1д" marker is
     * appended (25:00 → "25:00 +1д") to flag the next-day jump instead of a silent 25:00.
     */
    fun formatDisplayTime(offsetMinutes: Int): String {
        val clock = "%02d:%02d".format(offsetMinutes / 60, offsetMinutes % 60)
        return if (offsetMinutes >= 1440) "$clock +1д" else clock
    }

    fun logBadge(l: Lang): String = pick(l, " · 📒 log", " · 📒 журнал")

    fun trackAddedDetailed(l: Lang, h: Track): String {
        val type = trackTypeLabel(l, h) + if (h.logOnly) logBadge(l) else ""
        val times = h.reminders.joinToString(", ") { rem ->
            val d = if (rem.days.isNotEmpty()) " (${formatDays(l, rem.days)})" else ""
            "${formatDisplayTime(rem.offsetMinutes)}$d"
        }
        if (h.multiField) {
            val header = pick(l, "Added: \"${h.name}\" [$type]", "Добавлено: «${h.name}» [$type]")
            val fieldLines = h.params.map { f ->
                if (f.paramType == ParamType.TEXT) {
                    val label = pick(l, "text", "текст")
                    "  – ${f.name ?: ""} ($label)"
                } else {
                    val unit = f.unit?.let { " $it" } ?: ""
                    val target = f.dailyTarget?.let { " — ${formatAmount(it)}$unit/day" } ?: ""
                    val dir = f.direction?.let { " (${directionLabel(l, it)})" } ?: ""
                    "  – ${f.name ?: ""}$target$dir"
                }
            }
            val timesLine = if (times.isNotEmpty()) listOf("  ⏰ $times") else emptyList()
            return (listOf(header) + fieldLines + timesLine).joinToString("\n")
        }
        val tail = buildString {
            when (h.type) {
                TrackType.CHECK -> {
                    if (h.allowAdHoc) {
                        append(" — ${pick(l, "any time", "в любое время")}")
                        h.dailyTarget?.toInt()?.let { append(" — ${pick(l, "target: $it/day", "цель: $it/день")}") }
                        h.direction?.let { append(" — ${directionLabel(l, it)}") }
                    }
                    if (times.isNotEmpty()) append(" — $times")
                }
                TrackType.QUANTITY -> {
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
                TrackType.TIMER -> {
                    h.dailyTarget?.let {
                        append(" — ${pick(l, "target: ${formatDuration(l, it)}/day", "цель: ${formatDuration(l, it)}/день")}")
                    }
                    h.direction?.let { append(" — ${directionLabel(l, it)}") }
                    if (times.isNotEmpty()) append(" — $times")
                }
            }
        }
        return pick(l, "Added: \"${h.name}\" [$type]$tail", "Добавлено: «${h.name}» [$type]$tail")
    }

    fun noTracks(l: Lang) = pick(l,
        "No tracks yet. Add one with /addtrack.",
        "Треков ещё нет. Добавьте через /addtrack.")

    fun yourTracks(l: Lang) = pick(l, "Your tracks:", "Ваши треки:")

    fun noReminders(l: Lang) = pick(l, "no reminders", "без напоминаний")

    fun nothingToRemove(l: Lang) = pick(l, "Nothing to remove.", "Удалять нечего.")

    fun pickTrackToRemove(l: Lang) = pick(l, "Pick a track to remove:", "Выберите трек для удаления:")

    fun noParamsToDelete(l: Lang) = pick(l, "No fields to delete.", "Нет полей для удаления.")

    fun pickTrackForParam(l: Lang) = pick(l, "Pick a track:", "Выберите трек:")

    fun pickParamToDelete(l: Lang) = pick(l, "Pick a field to delete:", "Выберите поле для удаления:")

    fun noActiveToPause(l: Lang) = pick(l, "No active tracks to pause.", "Нет активных треков для паузы.")

    fun pickTrackToPause(l: Lang) = pick(l, "Pick a track to pause:", "Выберите трек для паузы:")

    fun pickPauseDuration(l: Lang) = pick(l, "For how long?", "На сколько?")

    fun btnPauseDay(l: Lang) = pick(l, "1 day", "1 день")
    fun btnPause3Days(l: Lang) = pick(l, "3 days", "3 дня")
    fun btnPauseWeek(l: Lang) = pick(l, "1 week", "1 неделя")
    fun btnPauseMonth(l: Lang) = pick(l, "1 month", "1 месяц")
    fun btnPauseForever(l: Lang) = pick(l, "Until I resume", "Пока не возобновлю")
    fun btnPauseCustom(l: Lang) = pick(l, "Other…", "Другое…")

    fun askPauseDuration(l: Lang) = pick(
        l,
        "Send the duration: a number of days, or like 2w / 1m.",
        "Пришлите длительность: число дней, либо вида 2w / 1m.",
    )
    fun cbBadDuration(l: Lang) = pick(l, "Couldn't read that duration.", "Не понял длительность.")

    fun autoResumed(l: Lang, name: String) = pick(
        l,
        "▶️ \"$name\" is active again — the pause has ended.",
        "▶️ «$name» снова активна — пауза закончилась.",
    )

    fun noPaused(l: Lang) = pick(l, "No paused tracks.", "Нет треков на паузе.")

    fun pickTrackToResume(l: Lang) = pick(l, "Pick a track to resume:", "Выберите трек для возобновления:")

    fun nothingToCheckIn(l: Lang) = pick(l, "Nothing to check in.", "Чек-инить нечего.")

    fun pendingCheckIns(l: Lang) = pick(l, "Pending check-ins:", "Ожидают чек-ина:")

    fun counterLine(l: Lang, h: Track, current: Int, date: LocalDate): String {
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

    // ---- recent check-ins log (/log) ----

    fun recentHeader(l: Lang) = pick(l, "🗒 Recent check-ins:", "🗒 Последние чек-ины:")

    fun noRecentCheckins(l: Lang) = pick(l, "No check-ins yet.", "Чек-инов пока нет.")

    fun btnPrevPage(l: Lang) = pick(l, "◀️ Newer", "◀️ Новее")
    fun btnNextPage(l: Lang) = pick(l, "Older ▶️", "Старее ▶️")

    /**
     * One check-in rendered for the /log listing: a "date · track" title, the recorded values
     * (or a done/skip/pending icon for a scheduled slot), and the comment when present.
     */
    fun recentBlock(l: Lang, track: Track?, rc: RecentCheckin): String {
        val name = track?.name ?: "#${rc.trackId}"
        val title = "${rc.date} · $name"
        val comment = rc.comment?.let { "\n  💬 $it" } ?: ""
        // A scheduled slot carries no values — just its done/skip/pending status.
        if (rc.reminderId != null) {
            val icon = when (rc.status) {
                CheckinStatus.DONE -> "✅"
                CheckinStatus.SKIP -> "❌"
                else -> "⏳"
            }
            return "$icon $title$comment"
        }
        val valueLines = rc.values.mapNotNull { v ->
            val param = track?.params?.firstOrNull { it.id == v.paramId }
            val label = param?.name?.let { "$it: " } ?: ""
            when (val fv = v.value) {
                is FieldValue.Numeric -> "  – $label${paramAmount(l, track, param, fv.v)}"
                is FieldValue.Text -> "  – $label${fv.v}"
                null -> null
            }
        }
        return "• $title" + (if (valueLines.isEmpty()) "" else "\n" + valueLines.joinToString("\n")) + comment
    }

    // ---- timer ----

    fun tzRequiredTimer(l: Lang) = pick(l,
        "Set your timezone first with /tz <IANA name>.",
        "Сначала задайте часовой пояс через /tz <IANA>.")

    fun noTimers(l: Lang) = pick(l,
        "No timer tracks yet. Add one with /addtrack (type ⏱ timer).",
        "Таймеров пока нет. Добавьте через /addtrack (тип ⏱ таймер).")

    fun yourTimers(l: Lang) = pick(l, "Timers:", "Таймеры:")

    fun btnTimerStart(l: Lang) = pick(l, "▶️ Start", "▶️ Старт")
    fun btnTimerStop(l: Lang) = pick(l, "⏹ Stop", "⏹ Стоп")
    fun btnTimerStopComment(l: Lang) = pick(l, "⏹💬 Stop + note", "⏹💬 Стоп + заметка")
    fun btnTimerPause(l: Lang) = pick(l, "⏸ Pause", "⏸ Пауза")
    fun btnTimerResume(l: Lang) = pick(l, "▶️ Resume", "▶️ Продолжить")

    fun sendTimerComment(l: Lang) = pick(l,
        "Send a note for this session:",
        "Отправьте заметку к этой сессии:")

    fun sendFirstTimerFieldNameOrSkip(l: Lang) = pick(l,
        "Extra fields to fill before/after each session? Send a field name, or \"-\" to skip.",
        "Дополнительные поля для заполнения до/после сессии? Отправьте название поля или «-», чтобы пропустить.")

    fun sendNextTimerFieldNameOrDone(l: Lang) = pick(l,
        "Another field? Send its name, or \"done\" to finish.",
        "Ещё поле? Отправьте название или «готово», чтобы закончить.")

    fun pickTimerFieldPhase(l: Lang) = pick(l,
        "When is this field filled in?",
        "Когда заполнять это поле?")

    fun btnPhaseBefore(l: Lang) = pick(l, "▶️ Before start", "▶️ До старта")
    fun btnPhaseAfter(l: Lang) = pick(l, "⏹ After stop", "⏹ После стопа")

    fun sendTimerFieldValue(l: Lang, name: String) = pick(l,
        "$name (send \"-\" to skip):",
        "$name (отправьте «-», чтобы пропустить):")

    fun timerFieldNotANumber(l: Lang, name: String) = pick(l,
        "\"$name\" must be a number (e.g. 1.5). Try again, or send \"-\" to skip:",
        "«$name» должно быть числом (например 1.5). Попробуйте ещё раз или отправьте «-», чтобы пропустить:")

    /** A timer track's line: shows running-since elapsed time, or today's accumulated total when idle. */
    fun timerLine(l: Lang, h: Track, running: Boolean, elapsedSeconds: Double, todaySeconds: Double, paused: Boolean = false): String {
        val target = h.dailyTarget
        return if (running) {
            val state = if (paused) pick(l, "paused", "пауза") else pick(l, "running", "идёт")
            "⏱ ${h.name} — $state: ${formatElapsed(elapsedSeconds)}"
        } else {
            val todayPart = pick(l, "today: ${formatDuration(l, todaySeconds)}", "сегодня: ${formatDuration(l, todaySeconds)}")
            val targetPart = target?.let { " / ${formatDuration(l, it)}" } ?: ""
            "⏱ ${h.name} — $todayPart$targetPart"
        }
    }

    fun cbTimerStarted(l: Lang) = pick(l, "Timer started", "Таймер запущен")
    fun cbTimerAlreadyRunning(l: Lang) = pick(l, "Already running", "Уже идёт")
    fun cbTimerStopped(l: Lang, seconds: Double) =
        pick(l, "Logged ${formatDuration(l, seconds)}", "Записано ${formatDuration(l, seconds)}")
    fun cbTimerNotRunning(l: Lang) = pick(l, "Timer wasn't running", "Таймер не был запущен")
    fun cbTimerPaused(l: Lang) = pick(l, "Paused", "Пауза")
    fun cbTimerResumed(l: Lang) = pick(l, "Resumed", "Продолжено")

    fun trackTypeLabel(l: Lang, h: Track): String = when (h.type) {
        TrackType.CHECK -> pick(l, "check", "отметка")
        TrackType.QUANTITY -> pick(l, "quantity", "количество")
        TrackType.TIMER -> pick(l, "timer", "таймер")
    }

    /** Renders elapsed seconds for a live timer, e.g. "1:05:30" or "12:30". */
    fun formatElapsed(seconds: Double): String {
        val totalSec = seconds.toLong()
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    /** Renders a second count as a compact duration, e.g. "1h 05m" / "1ч 05м" or "12m" / "12м". */
    fun formatDuration(l: Lang, seconds: Double): String {
        val totalMin = (seconds / 60.0).toLong()
        val h = totalMin / 60
        val m = totalMin % 60
        return when {
            h > 0 -> pick(l, "${h}h %02dm".format(m), "${h}ч %02dм".format(m))
            else -> pick(l, "${m}m", "${m}м")
        }
    }

    /**
     * Display value of a numeric param. A timer's duration param (the `timerPhase == null` field of a
     * TIMER track) stores elapsed seconds, so render it as a duration ("50м") rather than a raw count;
     * every other numeric param is a plain amount plus its unit.
     */
    fun paramAmount(l: Lang, track: Track?, param: TrackParam?, v: Double): String =
        if (track?.type == TrackType.TIMER && param?.timerPhase == null) {
            formatDuration(l, v)
        } else {
            formatAmount(v) + ((param?.unit ?: track?.unit)?.let { " $it" } ?: "")
        }

    fun formatAmount(v: Double): String {
        if (v.isNaN() || v.isInfinite()) return "0"
        val rounded = (v * 1000.0).roundToLong() / 1000.0
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

    fun noStats(l: Lang) = pick(l, "No tracks to report on.", "Не по чем статистику показывать.")

    fun statsHeader(l: Lang) = pick(l, "Stats:", "Статистика:")

    fun statsCompletion(l: Lang) = pick(l, "completion", "выполнение")

    fun statsStreak(l: Lang, days: Int) = pick(l,
        "🔥 streak: $days day(s)",
        "🔥 серия: $days дн.")

    fun statsLines(l: Lang, s: TrackStat): List<String> {
        val pct = if (s.totalDays > 0) "%.0f%%".format(java.util.Locale.ROOT, s.loggedDays * 100.0 / s.totalDays) else "—"
        val days = pick(l,
            "📅 ${s.loggedDays}/${s.totalDays} days ($pct)",
            "📅 ${s.loggedDays}/${s.totalDays} дн. ($pct)")
        // Log-only tracks (e.g. log-only timers) are journals: skip the streak/completion line and
        // just show their recorded values below.
        val lines = if (s.logOnly) mutableListOf() else mutableListOf("${statsStreak(l, s.streak)}   $days")
        s.trend?.let { lines += trendLine(l, it) }
        s.groupFields.forEach { f ->
            f.trend?.let { lines += "${f.name}: ${trendLine(l, it)}" }
        }
        return lines
    }

    private fun trendLine(l: Lang, t: dto.QuantityTrend): String {
        if (t.isDuration) return timerTrendLine(l, t)
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

    /**
     * Timer trend: values are seconds rendered as durations. When the track has a daily target
     * ("со статами") it gets a today-vs-target verdict (✅ reached / ⏳ not yet); a log-only timer
     * ("без статов") just shows its recorded time.
     */
    private fun timerTrendLine(l: Lang, t: dto.QuantityTrend): String {
        val today = formatDuration(l, t.today)
        val recent = formatDuration(l, t.recentAvg)
        val overall = formatDuration(l, t.overallAvg)
        val targetPart = t.target?.let { tgt ->
            val mark = if (t.today >= tgt) "✅" else "⏳"
            " / ${formatDuration(l, tgt)} $mark"
        } ?: ""
        return pick(l,
            "⏱ $today$targetPart · ${t.windowDays}d $recent · all $overall",
            "⏱ $today$targetPart · ${t.windowDays}д $recent · всё $overall")
    }

    fun weeklySummary(
        l: Lang,
        from: LocalDate,
        to: LocalDate,
        stats: List<TrackWeekStat>
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
                    // A check track shows its scheduled (done/skip) block when it has reminders and
                    // its ad-hoc counter block when it allows ad-hoc check-ins; both may appear.
                    TrackType.CHECK -> {
                        if (s.hasSchedule) {
                            val total = s.scheduledDone + s.scheduledSkip
                            val rate = if (total > 0) "%.0f%%".format(java.util.Locale.ROOT, s.scheduledDone * 100.0 / total) else "—"
                            appendLine("    ✅ ${s.scheduledDone}   ❌ ${s.scheduledSkip}   ${statsCompletion(l)}: $rate")
                        }
                        if (s.allowAdHoc) {
                            val avg = if (s.counterDays > 0) "%.1f".format(java.util.Locale.ROOT, s.counterTotal.toDouble() / s.counterDays) else "0"
                            val dirSuffix = s.direction?.let { "   (${directionShort(l, it)})" } ?: ""
                            appendLine(pick(l,
                                "    total: ${s.counterTotal}   days: ${s.counterDays}   avg/day: $avg$dirSuffix",
                                "    всего: ${s.counterTotal}   дней: ${s.counterDays}   среднее: $avg$dirSuffix"))
                            if (s.dailyTarget != null) {
                                appendLine(pick(l,
                                    "    🎯 target hit: ${s.targetHitDays}/7",
                                    "    🎯 цель достигнута: ${s.targetHitDays}/7"))
                            }
                        }
                    }
                    TrackType.QUANTITY -> {
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
                    TrackType.TIMER -> {
                        val total = formatDuration(l, s.quantityTotal)
                        val avg = if (s.quantityDays > 0) formatDuration(l, s.quantityTotal / s.quantityDays) else formatDuration(l, 0.0)
                        val dirSuffix = s.direction?.let { "   (${directionShort(l, it)})" } ?: ""
                        appendLine(pick(l,
                            "    total: $total   days: ${s.quantityDays}   avg/day: $avg$dirSuffix",
                            "    всего: $total   дней: ${s.quantityDays}   среднее: $avg$dirSuffix"))
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

    fun calTitle(l: Lang) = pick(l, "📅 Track calendar subscription", "📅 Подписка на календарь треков")

    fun calHowTo(l: Lang) = pick(l,
        "Add this URL in your calendar app (Google/Apple/Outlook) as a subscribed calendar:",
        "Добавьте эту ссылку в календарь (Google/Apple/Outlook) как подписной календарь:")

    fun calToggleHint(l: Lang) = pick(l,
        "Choose what shows up in the calendar:",
        "Выберите, что показывать в календаре:")

    fun calCheckins(l: Lang) = pick(l, "Check-ins", "Чек-ины")

    fun calReminders(l: Lang) = pick(l, "Reminders", "Напоминания")

    fun calNewLink(l: Lang) = pick(l, "🔄 New link", "🔄 Новая ссылка")

    fun calLinkReset(l: Lang) = pick(l,
        "New link created — the old one no longer works.",
        "Новая ссылка создана — старая больше не работает.")

    fun calUpdated(l: Lang) = pick(l, "Updated", "Обновлено")

    fun calOff(l: Lang) = pick(l,
        "Calendar subscription disabled. The link no longer works.",
        "Подписка на календарь отключена. Ссылка больше не работает.")

    fun calNoSub(l: Lang) = pick(l,
        "No active calendar subscription. Create one with /calendar.",
        "Активной подписки нет. Создайте через /calendar.")

    fun mcpRecordedQuantity(l: Lang, name: String, amount: Double, unit: String?, date: LocalDate, comment: String?): String {
        val u = unit?.let { " $it" } ?: ""
        val c = comment?.let { "\n💬 $it" } ?: ""
        return pick(l,
            "🤖 via MCP — $name: ${formatAmount(amount)}$u — $date$c",
            "🤖 через MCP — $name: ${formatAmount(amount)}$u — $date$c")
    }

    fun mcpRecordedCheck(l: Lang, h: Track, total: Int, date: LocalDate, comment: String?): String {
        val c = comment?.let { "\n💬 $it" } ?: ""
        return counterLine(l, h, total, date).let { line ->
            pick(l, "🤖 via MCP — $line$c", "🤖 через MCP — $line$c")
        }
    }

    fun mcpRecordedQuantityGroup(
        l: Lang,
        root: Track,
        numericPerField: Map<Long, Double>,
        textPerField: Map<Long, String> = emptyMap(),
        date: LocalDate,
        comment: String?
    ): String {
        val header = pick(l,
            "🤖 via MCP — ${root.name} — $date",
            "🤖 через MCP — ${root.name} — $date")
        val fieldLines = root.params.mapNotNull { f ->
            val numV = numericPerField[f.id]
            val textV = textPerField[f.id]
            when {
                numV != null -> { val u = f.unit?.let { " $it" } ?: ""; "  – ${f.name ?: ""}: ${formatAmount(numV)}$u" }
                textV != null -> "  – ${f.name ?: ""}: $textV"
                else -> null
            }
        }
        val body = (listOf(header) + fieldLines).joinToString("\n")
        return comment?.let { "$body\n💬 $it" } ?: body
    }

    fun mcpRecordedQuantityText(l: Lang, name: String, text: String, date: LocalDate, comment: String?): String {
        val c = comment?.let { "\n💬 $it" } ?: ""
        return pick(l,
            "🤖 via MCP — $name: $text — $date$c",
            "🤖 через MCP — $name: $text — $date$c")
    }

    fun mcpDeletedCheckin(l: Lang, lines: List<String>, date: LocalDate): String {
        val header = pick(l,
            "🗑 via MCP — check-in removed — $date",
            "🗑 через MCP — чек-ин удалён — $date")
        return (listOf(header) + lines.map { "  – $it" }).joinToString("\n")
    }

    fun mcpUpdatedCheckin(l: Lang, lines: List<String>, date: LocalDate): String {
        val header = pick(l,
            "✏️ via MCP — check-in updated — $date",
            "✏️ через MCP — чек-ин обновлён — $date")
        return (listOf(header) + lines.map { "  – $it" }).joinToString("\n")
    }


    fun mcpTrackUpdated(l: Lang, name: String, lines: List<String>): String {
        val header = pick(l,
            "✏️ via MCP — track «$name» updated",
            "✏️ через MCP — трек «$name» обновлён")
        return (listOf(header) + lines.map { "  – $it" }).joinToString("\n")
    }

    fun mcpTrackPaused(l: Lang, name: String, days: Int): String = pick(l,
        if (days > 0) "⏸ via MCP — track «$name» paused for $days day(s)" else "⏸ via MCP — track «$name» paused",
        if (days > 0) "⏸ через MCP — трек «$name» на паузе $days дн." else "⏸ через MCP — трек «$name» на паузе")

    fun mcpTrackResumed(l: Lang, name: String) = pick(l,
        "▶️ via MCP — track «$name» resumed", "▶️ через MCP — трек «$name» возобновлён")

    fun mcpTrackDeleted(l: Lang, name: String) = pick(l,
        "🗑 via MCP — track «$name» deleted", "🗑 через MCP — трек «$name» удалён")

    fun btnDone(l: Lang) = pick(l, "✅ Complete", "✅ Готово")
    fun btnDelete(l: Lang) = pick(l, "🗑 Delete", "🗑 Удалить")
    fun btnPlusOne(l: Lang) = pick(l, "➕1", "➕1")
    fun btnPlusComment(l: Lang) = pick(l, "💬 +1", "💬 +1")

    fun sendCounterComment(l: Lang) = pick(l,
        "Send a comment for this +1:",
        "Отправьте комментарий к этому +1:")
    fun pickParamType(l: Lang) = pick(l, "Field type:", "Тип поля:")
    fun btnParamTypeNumber(l: Lang) = pick(l, "🔢 number", "🔢 число")
    fun btnParamTypeText(l: Lang) = pick(l, "📝 text", "📝 текст")

    fun pickLogMode(l: Lang) = pick(l,
        "Track metrics, or just keep a log?\n• tracked — streaks, completion, trends and weekly summary\n• log only — just a journal of tracks, no targets/streaks, hidden from /stats",
        "Считать метрики или просто вести журнал?\n• с метриками — серии, выполнение, тренды и недельная сводка\n• только журнал — лог треков без целей/серий, скрыт из /stats")

    fun btnTracked(l: Lang) = pick(l, "📊 tracked", "📊 с метриками")
    fun btnLogOnly(l: Lang) = pick(l, "📒 log only", "📒 только журнал")

    fun askAllowAdHoc(l: Lang) = pick(l,
        "Allow logging check-ins any time (a \"+1\" you can press whenever), on top of any scheduled times?",
        "Разрешить отмечать в любое время (кнопка «+1», когда угодно), помимо времени по расписанию?")

    fun btnAdHocYes(l: Lang) = pick(l, "✅ yes, any time", "✅ да, в любое время")
    fun btnAdHocNo(l: Lang) = pick(l, "📅 no, scheduled only", "📅 нет, только по расписанию")

    fun checkNeedsScheduleOrAdHoc(l: Lang) = pick(l,
        "A check track needs a schedule and/or ad-hoc check-ins — you chose neither. Start over with /addtrack.",
        "Треку типа «отметка» нужно расписание и/или отметки в любое время — вы не выбрали ни то, ни другое. Начните заново через /addtrack.")

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
    fun cbRemovedFull(l: Lang) = pick(l, "Track removed.", "Трек удалён.")
    fun cbParamDeleted(l: Lang) = pick(l, "Field deleted. 🗑", "Поле удалено. 🗑")
    fun cbPausedShort(l: Lang) = pick(l, "Paused", "На паузе")
    fun cbPausedFull(l: Lang) = pick(l, "Track paused.", "Трек на паузе.")
    fun cbPausedForDays(l: Lang, days: Int) = pick(
        l,
        "Paused for $days ${if (days == 1) "day" else "days"} — it'll resume on its own.",
        "На паузе на $days ${plural(days, "день", "дня", "дней")} — возобновится сама.",
    )
    fun cbPausedForever(l: Lang) = pick(l, "Paused until you resume it.", "На паузе, пока не возобновите.")
    fun cbResumedShort(l: Lang) = pick(l, "Resumed", "Возобновлено")
    fun cbResumedFull(l: Lang) = pick(l, "Track resumed.", "Трек возобновлён.")

    private fun pick(l: Lang, en: String, ru: String): String = if (l == Lang.RU) ru else en

    /** Russian numeric plural: picks one / few / many by the standard 1, 2-4, 0/5+ rules. */
    private fun plural(n: Int, one: String, few: String, many: String): String {
        val mod100 = n % 100
        val mod10 = n % 10
        return when {
            mod100 in 11..14 -> many
            mod10 == 1 -> one
            mod10 in 2..4 -> few
            else -> many
        }
    }
}

/** Bot name/description/short-description texts, wired up via the (currently commented) profile setup in Main.kt. */
@Suppress("unused")
object BotProfileI18n {
    fun name(l: Lang): String = when (l) {
        Lang.EN -> "TrackAll"
        Lang.RU -> "TrackAll"
    }

    fun description(l: Lang): String = when (l) {
        Lang.EN -> """
            TrackAll. Set up daily tracks, get reminders at the times you choose, and mark each one done or skipped.

            Tap /start to begin or /addtrack to add your first track.
        """.trimIndent()
        Lang.RU -> """
            TrackAll. Заводите ежедневные треки, получайте напоминания в нужное время и отмечайте «готово» или «пропуск».

            Нажмите /start, чтобы начать, или /addtrack, чтобы добавить первый трек.
        """.trimIndent()
    }

    fun shortDescription(l: Lang): String = when (l) {
        Lang.EN -> "Track anything with reminders, check-ins and streaks."
        Lang.RU -> "Трекинг чего угодно: напоминания, чек-ины и серии."
    }
}

object BotCommandsI18n {
    fun list(l: Lang): List<Pair<String, String>> = when (l) {
        Lang.EN -> listOf(
            "checkin" to "today's check-ins",
            "log" to "recent check-ins",
            "timer" to "start/stop time tracking",
            "stats" to "statistics",
            "tracks" to "list tracks",
            "addtrack" to "add a track (interactive)",
            "cancel" to "cancel the current /addtrack dialog",
            "pause" to "pause a track",
            "resume" to "resume a paused track",
            "removetrack" to "remove a track",
            "deleteparam" to "delete a track field",
            "tz" to "show or set your timezone",
            "lang" to "switch language (en/ru)",
            "calendar" to "subscribe to a track calendar (iCal)",
            "calendar_off" to "disable the calendar subscription",
            "mcp_new" to "create an MCP API token",
            "mcp_list" to "list MCP API tokens",
            "mcp_revoke" to "revoke an MCP API token",
        )
        Lang.RU -> listOf(
            "checkin" to "чек-ины за сегодня",
            "log" to "последние чек-ины",
            "timer" to "старт/стоп отслеживания времени",
            "stats" to "статистика",
            "tracks" to "список треков",
            "addtrack" to "добавить трек (интерактивно)",
            "cancel" to "отменить текущий диалог /addtrack",
            "pause" to "поставить трек на паузу",
            "resume" to "возобновить трек",
            "removetrack" to "удалить трек",
            "deleteparam" to "удалить поле трека",
            "tz" to "показать или задать часовой пояс",
            "lang" to "сменить язык (en/ru)",
            "calendar" to "подписка на календарь треков (iCal)",
            "calendar_off" to "отключить подписку на календарь",
            "mcp_new" to "создать токен MCP API",
            "mcp_list" to "список токенов MCP API",
            "mcp_revoke" to "отозвать токен MCP API",
        )
    }
}
