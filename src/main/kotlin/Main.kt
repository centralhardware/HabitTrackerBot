import ReminderScheduler.runOnce
import WeeklySummaryScheduler.runWeeklyOnce
import commands.registerAddHabitCommand
import commands.registerCheckInCommand
import commands.registerHabitsCommand
import commands.registerPauseCommand
import commands.registerRemoveHabitCommand
import commands.registerResumeCommand
import commands.registerLangCommand
import commands.registerStartCommand
import commands.registerStatsCommand
import commands.registerTzCommand
import dev.inmo.krontab.doInfinity
import dev.inmo.micro_utils.common.Warning
import dev.inmo.tgbotapi.AppConfig
import dev.inmo.tgbotapi.extensions.api.bot.setMyCommands
import dev.inmo.tgbotapi.extensions.api.bot.setMyDescription
import dev.inmo.tgbotapi.extensions.api.bot.setMyName
import dev.inmo.tgbotapi.extensions.api.bot.setMyShortDescription
import dev.inmo.tgbotapi.extensions.api.chat.modify.setDefaultChatMenuButton
import dev.inmo.tgbotapi.longPolling
import dev.inmo.tgbotapi.types.BotCommand
import dev.inmo.tgbotapi.types.MenuButton
import dev.inmo.tgbotapi.types.commands.BotCommandScope.Companion.Default
import dev.inmo.tgbotapi.utils.RiskFeature
import kotlinx.coroutines.launch

@OptIn(Warning::class, RiskFeature::class)
suspend fun main() {
    DatabaseService.dataSource

    longPolling("HabitTrackerBot") {
        Lang.entries.forEach { lang ->
            val code = if (lang == Lang.RU) "ru" else "en"
            val commands = BotCommandsI18n.list(lang).map { (name, desc) -> BotCommand(name, desc) }
            setMyCommands(
                commands = commands,
                scope = Default,
                languageCode = code
            )
            setMyName(BotProfileI18n.name(lang), code)
            setMyDescription(BotProfileI18n.description(lang), code)
            setMyShortDescription(BotProfileI18n.shortDescription(lang), code)
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
        registerCallbackHandler()

        launch {
            doInfinity("0 /1 * * *") {
                runOnce()
                runWeeklyOnce()
            }
        }
    }.second.join()
}
