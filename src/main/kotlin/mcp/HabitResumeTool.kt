package mcp

import BotNotifier
import HabitService
import Lang
import Strings
import dto.HabitIdArgs
import dto.McpJson
import dto.McpProp
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.decodeFromJsonElement
import java.time.ZoneId

object HabitResumeTool : McpTool {
    override val name = "habit_resume"
    override val description =
        "Resume a paused habit so its reminders fire again. 'habitId' is the habit id (group root for multi-field habits). Pause with 'habit_pause'."
    override val inputSchema: ToolSchema = toolSchema(
        mapOf("habitId" to McpProp(type = "integer")),
        required = listOf("habitId"),
    )

    override fun handle(userId: Long, lang: Lang, tz: ZoneId, request: CallToolRequest): CallToolResult {
        val rawArgs = request.arguments ?: return err("arguments required")
        val args = runCatching { McpJson.decodeFromJsonElement<HabitIdArgs>(rawArgs) }
            .getOrElse { return err("Invalid arguments: ${it.message}") }
        val habit = HabitService.findById(args.habitId, userId)
            ?: return err("Habit ${args.habitId} not found")
        if (!HabitService.resume(args.habitId, userId)) {
            return err("Habit ${args.habitId} is not paused; cannot resume")
        }
        BotNotifier.notify(userId, Strings.mcpResumedHabit(lang, habit.name))
        return ok("""{"resumed":true,"habitId":${args.habitId}}""")
    }
}
