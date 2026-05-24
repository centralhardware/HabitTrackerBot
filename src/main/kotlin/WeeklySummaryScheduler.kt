import dev.inmo.kslog.common.KSLog
import dev.inmo.kslog.common.info
import dev.inmo.tgbotapi.extensions.api.send.sendMessage
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContext
import dev.inmo.tgbotapi.types.toChatId
import kotliquery.queryOf
import kotliquery.sessionOf
import java.time.LocalDate

object WeeklySummaryScheduler {

    private const val SEND_HOUR = 20
    private const val SEND_DOW = 0 // Sunday (PostgreSQL DOW: 0=Sunday)

    suspend fun BehaviourContext.runWeeklyOnce() {
        val due = findDueUsers()
        due.forEach { user ->
            runCatching {
                val to = user.today
                val from = to.minusDays(6)
                val stats = WeeklySummaryService.weeklyStats(user.userId, from, to)
                val text = Strings.weeklySummary(user.lang, from, to, stats) ?: return@forEach
                sendMessage(user.userId.toChatId(), text)
            }.onFailure { e ->
                KSLog.info("Failed to send weekly summary to ${user.userId}: ${e.message}")
            }
        }
    }

    private data class DueUser(val userId: Long, val today: LocalDate, val lang: Lang)

    private fun findDueUsers(): List<DueUser> {
        return sessionOf(DatabaseService.dataSource).use { session ->
            session.run(
                queryOf(
                    """
                    SELECT us.user_id,
                           (now() AT TIME ZONE us.timezone)::date AS today,
                           us.language
                    FROM user_settings us
                    WHERE us.timezone IS NOT NULL
                      AND EXTRACT(DOW    FROM (now() AT TIME ZONE us.timezone))::int = ?
                      AND EXTRACT(HOUR   FROM (now() AT TIME ZONE us.timezone))::int = ?
                      AND EXTRACT(MINUTE FROM (now() AT TIME ZONE us.timezone))::int = 0
                    """.trimIndent(),
                    SEND_DOW,
                    SEND_HOUR
                ).map { row ->
                    val langCode = row.stringOrNull("language")
                    val lang = langCode?.let { runCatching { Lang.valueOf(it) }.getOrNull() } ?: Lang.EN
                    DueUser(
                        userId = row.long("user_id"),
                        today = row.localDate("today"),
                        lang = lang
                    )
                }.asList
            )
        }
    }
}
