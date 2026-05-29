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

object HabitPauseTool : TypedMcpTool<HabitIdArgs>(HabitIdArgs.serializer()) {
    override val name = "habit_pause"
    override val description =
        "Pause an active habit so its reminders stop firing and it is excluded from new check-ins. 'habitId' is the habit id (group root for multi-field habits). Resume later with 'habit_resume'."
    override val inputSchema: ToolSchema = toolSchema(
        mapOf("habitId" to McpProp(type = "integer")),
        required = listOf("habitId"),
    )

    override fun handle(userId: Long, lang: Lang, tz: ZoneId, args: HabitIdArgs): CallToolResult {
        val habit = HabitService.findById(args.habitId, userId)
            ?: return err("Habit ${args.habitId} not found")
        if (!HabitService.pause(args.habitId, userId)) {
            return err("Habit ${args.habitId} is not active; cannot pause")
        }
        BotNotifier.notify(userId, Strings.mcpPausedHabit(lang, habit.name))
        return ok("""{"paused":true,"habitId":${args.habitId}}""")
    }
}
