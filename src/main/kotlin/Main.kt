import ReminderScheduler.sendDueReminders
import TimerTicker.tickRunningTimers
import commands.addhabit.registerAddHabitCommand
import commands.registerCheckInCommand
import commands.registerDeleteParamCommand
import commands.registerHabitsCommand
import commands.registerMcpCommands
import commands.registerPauseCommand
import commands.registerRemoveHabitCommand
import commands.registerResumeCommand
import commands.registerLangCommand
import commands.registerStartCommand
import commands.registerStatsCommand
import commands.registerTimerCommand
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
import kotlinx.coroutines.launch
import mcp.McpServer
import services.DatabaseService
import services.HabitService

@OptIn(Warning::class)
suspend fun main() {
    DatabaseService.dataSource

    McpServer.start()

    longPolling("HabitTrackerBot", subcontextInitialAction = populateUserContext) {
        BotNotifier.bind(this)
        Lang.entries.forEach { lang ->
            val code = if (lang == Lang.RU) "ru" else "en"
            val commands = BotCommandsI18n.list(lang).map { (name, desc) -> BotCommand(name, desc) }
            setMyCommands(
                commands = commands,
                scope = Default,
                languageCode = code
            )
        }
        setDefaultChatMenuButton(MenuButton.Commands)

        registerStartCommand()
        registerAddHabitCommand()
        registerHabitsCommand()
        registerRemoveHabitCommand()
        registerDeleteParamCommand()
        registerPauseCommand()
        registerResumeCommand()
        registerCheckInCommand()
        registerTimerCommand()
        registerStatsCommand()
        registerTzCommand()
        registerLangCommand()
        registerMcpCommands()
        registerCallbackHandler()

        launch {
            doInfinity("0 /1 * * *") {
                runCatching {
                    HabitService.autoResumeExpired().forEach { resumed ->
                        val lang = resumed.langCode?.let { runCatching { Lang.valueOf(it) }.getOrNull() } ?: Lang.EN
                        BotNotifier.notify(resumed.userId, Strings.autoResumed(lang, resumed.name))
                    }
                }.onFailure { KSLog.error("autoResumeExpired failed", it) }
                runCatching { sendDueReminders() }
                    .onFailure { KSLog.error("sendDueReminders failed", it) }
                runCatching { sendWeeklySummaries() }
                    .onFailure { KSLog.error("sendWeeklySummaries failed", it) }
            }
        }

        launch {
            doInfinity("* * * * *") {
                runCatching { tickRunningTimers() }
                    .onFailure { KSLog.error("tickRunningTimers failed", it) }
            }
        }
    }.second.join()
}
