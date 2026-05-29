package mcp

import BotNotifier
import HabitService
import Lang
import Strings
import dto.HabitIdArgs
import dto.McpProp
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import java.time.ZoneId

object HabitResumeTool : TypedMcpTool<HabitIdArgs>(HabitIdArgs.serializer()) {
    override val name = "habit_resume"
    override val description =
        "Resume a paused habit so its reminders fire again. 'habitId' is the habit id (group root for multi-field habits). Pause with 'habit_pause'."
    override val inputSchema: ToolSchema = toolSchema(
        mapOf("habitId" to McpProp(type = "integer")),
        required = listOf("habitId"),
    )

    override fun handle(userId: Long, lang: Lang, tz: ZoneId, args: HabitIdArgs): CallToolResult {
        val habit = HabitService.findById(args.habitId, userId)
            ?: return err("Habit ${args.habitId} not found")
        if (!HabitService.resume(args.habitId, userId)) {
            return err("Habit ${args.habitId} is not paused; cannot resume")
        }
        BotNotifier.notify(userId, Strings.mcpResumedHabit(lang, habit.name))
        return ok("""{"resumed":true,"habitId":${args.habitId}}""")
    }
}
