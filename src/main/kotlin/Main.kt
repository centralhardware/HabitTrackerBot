import ReminderScheduler.runOnce
import commands.registerAddHabitCommand
import commands.registerCheckInCommand
import commands.registerHabitsCommand
import commands.registerPauseCommand
import commands.registerRemoveHabitCommand
import commands.registerResumeCommand
import commands.registerStartCommand
import commands.registerStatsCommand
import commands.registerTzCommand
import dev.inmo.krontab.doInfinity
import dev.inmo.micro_utils.common.Warning
import dev.inmo.tgbotapi.AppConfig
import dev.inmo.tgbotapi.extensions.api.bot.setMyCommands
import dev.inmo.tgbotapi.longPolling
import dev.inmo.tgbotapi.types.BotCommand
import dev.inmo.tgbotapi.utils.RiskFeature
import kotlinx.coroutines.launch

@OptIn(Warning::class, RiskFeature::class)
suspend fun main() {
    DatabaseService.dataSource

    longPolling("HabitTrackerBot") {
        setMyCommands(
            BotCommand("start", "show help"),
            BotCommand("addhabit", "add a habit (interactive)"),
            BotCommand("habits", "list habits"),
            BotCommand("removehabit", "remove a habit"),
            BotCommand("pause", "pause a habit"),
            BotCommand("resume", "resume a paused habit"),
            BotCommand("checkin", "today's check-ins"),
            BotCommand("stats", "statistics"),
            BotCommand("tz", "show or set your timezone")
        )

        registerStartCommand()
        registerAddHabitCommand()
        registerHabitsCommand()
        registerRemoveHabitCommand()
        registerPauseCommand()
        registerResumeCommand()
        registerCheckInCommand()
        registerStatsCommand()
        registerTzCommand()
        registerCallbackHandler()

        launch {
            doInfinity("0 /1 * * *") {
                runOnce()
            }
        }
    }.second.join()
}
