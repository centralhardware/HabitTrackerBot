import ReminderScheduler.sendDueReminders
import WeeklySummaryScheduler.sendWeeklySummaries
import commands.registerAddHabitCommand
import commands.registerCheckInCommand
import commands.registerHabitsCommand
import commands.registerMcpCommands
import commands.registerPauseCommand
import commands.registerRemoveHabitCommand
import commands.registerResumeCommand
import commands.registerLangCommand
import commands.registerStartCommand
import commands.registerStatsCommand
import commands.registerTzCommand
import dev.inmo.krontab.doInfinity
import dev.inmo.kslog.common.KSLog
import dev.inmo.kslog.common.error
import dev.inmo.micro_utils.common.Warning
import dev.inmo.tgbotapi.extensions.api.bot.setMyCommands
import dev.inmo.tgbotapi.extensions.api.chat.modify.setDefaultChatMenuButton
import dev.inmo.tgbotapi.longPolling
import dev.inmo.tgbotapi.types.BotCommand
import dev.inmo.tgbotapi.types.MenuButton
import dev.inmo.tgbotapi.types.commands.BotCommandScope.Companion.Default
import dev.inmo.tgbotapi.utils.RiskFeature
import kotlinx.coroutines.launch
import mcp.McpServer
import services.DatabaseService

@OptIn(Warning::class, RiskFeature::class)
suspend fun main() {
    DatabaseService.dataSource

    if (Config.MCP_ENABLED) McpServer.start()

    longPolling("HabitTrackerBot") {
        BotNotifier.bind(this)
        Lang.entries.forEach { lang ->
            val code = if (lang == Lang.RU) "ru" else "en"
            val commands = BotCommandsI18n.list(lang).map { (name, desc) -> BotCommand(name, desc) }
            setMyCommands(
                commands = commands,
                scope = Default,
                languageCode = code
            )
//            setMyName(BotProfileI18n.name(lang), code)
//            setMyDescription(BotProfileI18n.description(lang), code)
//            setMyShortDescription(BotProfileI18n.shortDescription(lang), code)
        }
        setDefaultChatMenuButton(MenuButton.Commands)

        registerStartCommand()
        registerAddHabitCommand()
        registerHabitsCommand()
        registerRemoveHabitCommand()
        registerPauseCommand()
        registerResumeCommand()
        registerCheckInCommand()
        registerStatsCommand()
        registerTzCommand()
        registerLangCommand()
        registerMcpCommands()
        registerCallbackHandler()

        launch {
            doInfinity("0 /1 * * *") {
                runCatching { sendDueReminders() }
                    .onFailure { KSLog.error("sendDueReminders failed", it) }
                runCatching { sendWeeklySummaries() }
                    .onFailure { KSLog.error("sendWeeklySummaries failed", it) }
            }
        }
    }.second.join()
}
