import dev.inmo.tgbotapi.types.buttons.InlineKeyboardButtons.CallbackDataInlineKeyboardButton
import dev.inmo.tgbotapi.types.buttons.InlineKeyboardMarkup
import dto.Track
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object Keyboards {

    fun checkIn(reminderId: Long, date: LocalDate, lang: Lang): InlineKeyboardMarkup {
        return InlineKeyboardMarkup(
            listOf(
                listOf(
                    CallbackDataInlineKeyboardButton(Strings.btnDone(lang), "ci|$reminderId|$date|done")
                )
            )
        )
    }

    /** Start/stop/pause control for a timer track. Payload: `tm|<trackId>|<date>|start|stop|stopc|pause|resume`. */
    fun timerControl(trackId: Long, running: Boolean, date: LocalDate, lang: Lang, paused: Boolean = false): InlineKeyboardMarkup {
        if (!running)
            return InlineKeyboardMarkup(listOf(listOf(
                CallbackDataInlineKeyboardButton(Strings.btnTimerStart(lang), "tm|$trackId|$date|start")
            )))
        val pauseBtn = if (paused)
            CallbackDataInlineKeyboardButton(Strings.btnTimerResume(lang), "tm|$trackId|$date|resume")
        else
            CallbackDataInlineKeyboardButton(Strings.btnTimerPause(lang), "tm|$trackId|$date|pause")
        return InlineKeyboardMarkup(listOf(
            listOf(
                pauseBtn,
                CallbackDataInlineKeyboardButton(Strings.btnTimerStop(lang), "tm|$trackId|$date|stop"),
                CallbackDataInlineKeyboardButton(Strings.btnTimerStopComment(lang), "tm|$trackId|$date|stopc"),
            )
        ))
    }

    /** Duration choices shown after a track is picked for pausing. Payload: `pd|<trackId>|<days>` (0 = indefinite). */
    fun pauseDurations(trackId: Long, lang: Lang): InlineKeyboardMarkup {
        return InlineKeyboardMarkup(
            listOf(
                listOf(
                    CallbackDataInlineKeyboardButton(Strings.btnPauseDay(lang), "pd|$trackId|1"),
                    CallbackDataInlineKeyboardButton(Strings.btnPause3Days(lang), "pd|$trackId|3"),
                    CallbackDataInlineKeyboardButton(Strings.btnPauseWeek(lang), "pd|$trackId|7"),
                ),
                listOf(
                    CallbackDataInlineKeyboardButton(Strings.btnPauseMonth(lang), "pd|$trackId|30"),
                    CallbackDataInlineKeyboardButton(Strings.btnPauseForever(lang), "pd|$trackId|0"),
                ),
                listOf(
                    CallbackDataInlineKeyboardButton(Strings.btnPauseCustom(lang), "pc|$trackId"),
                ),
            )
        )
    }

    fun pickTrack(prefix: String, tracks: List<Track>, icon: String): InlineKeyboardMarkup {
        return InlineKeyboardMarkup(
            tracks.map { track ->
                listOf(
                    CallbackDataInlineKeyboardButton("$icon ${track.name}", "$prefix|${track.id}")
                )
            }
        )
    }

    /** Field choices shown after a track is picked for param deletion. Payload: `dp|<paramId>`. */
    fun pickParam(params: List<dto.TrackParam>, lang: Lang): InlineKeyboardMarkup {
        return InlineKeyboardMarkup(
            params.map { p ->
                listOf(
                    CallbackDataInlineKeyboardButton("🗑 ${p.name.orEmpty()}", "dp|${p.id}")
                )
            }
        )
    }

    /**
     * Prev/next pager for the /log recent-check-ins listing. Payload: `rc|<page>`.
     * Returns null when neither direction is available (so the single page shows no buttons).
     */
    fun recentPager(page: Int, hasPrev: Boolean, hasNext: Boolean, lang: Lang): InlineKeyboardMarkup? {
        val row = buildList {
            if (hasPrev) add(CallbackDataInlineKeyboardButton(Strings.btnPrevPage(lang), "rc|${page - 1}"))
            if (hasNext) add(CallbackDataInlineKeyboardButton(Strings.btnNextPage(lang), "rc|${page + 1}"))
        }
        return if (row.isEmpty()) null else InlineKeyboardMarkup(listOf(row))
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
