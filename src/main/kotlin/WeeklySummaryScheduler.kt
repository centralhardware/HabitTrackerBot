import db.SchedulerRepository
import dev.inmo.kslog.common.KSLog
import dev.inmo.kslog.common.info
import dev.inmo.tgbotapi.extensions.api.send.sendMessage
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContext
import dev.inmo.tgbotapi.types.toChatId
import services.WeeklySummaryService

object WeeklySummaryScheduler {

    private const val SEND_HOUR = 20
    private const val SEND_DOW = 0

    suspend fun BehaviourContext.sendWeeklySummaries() {
        SchedulerRepository.findDueWeeklyUsers(SEND_DOW, SEND_HOUR).forEach { user ->
            runCatching {
                val to = user.today
                val from = to.minusDays(6)
                val lang = user.langCode?.let { runCatching { Lang.valueOf(it) }.getOrNull() } ?: Lang.EN
                val stats = WeeklySummaryService.weeklyStats(user.userId, from, to)
                val text = Strings.weeklySummary(lang, from, to, stats) ?: return@forEach
                sendMessage(user.userId.toChatId(), text)
            }.onFailure { e ->
                KSLog.info("Failed to send weekly summary to ${user.userId}: ${e.message}")
            }
        }
    }
}
