import dev.inmo.kslog.common.KSLog
import dev.inmo.kslog.common.info
import dev.inmo.tgbotapi.extensions.api.send.sendMessage
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContext
import dev.inmo.tgbotapi.types.toChatId
import kotlinx.coroutines.launch

object BotNotifier {
    @Volatile private var ctx: BehaviourContext? = null

    fun bind(behaviourContext: BehaviourContext) {
        ctx = behaviourContext
    }

    fun notify(userId: Long, text: String) {
        val c = ctx ?: return
        c.launch {
            runCatching { c.sendMessage(userId.toChatId(), text) }
                .onFailure { e -> KSLog.info("BotNotifier failed for $userId: ${e.message}") }
        }
    }
}
