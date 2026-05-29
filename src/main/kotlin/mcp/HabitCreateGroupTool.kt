package mcp

import BotNotifier
import HabitService
import Lang
import Strings
import dto.Direction
import dto.HabitGroupCreateArgs
import dto.McpJson
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.time.ZoneId

object HabitCreateGroupTool : TypedMcpTool<HabitGroupCreateArgs>(HabitGroupCreateArgs.serializer()) {
    override val name = "habit_create_group"
    override val description =
        "Create a multi-field quantity habit: a group root with one field per metric. 'fields' is a non-empty array of { name, dailyTarget?, unit?, direction? }; each field's dailyTarget must be > 0 and direction is 'more' or 'less'. 'reminders' is an optional array of HH:MM strings shared by the group. Use 'habit_create' for single-value habits."
    override val inputSchema: ToolSchema = buildSchema()

    override fun handle(userId: Long, lang: Lang, tz: ZoneId, args: HabitGroupCreateArgs): CallToolResult {
        val name = args.name.trim()
        if (name.isBlank()) return err("'name' must not be blank")
        if (args.fields.isEmpty()) return err("'fields' must be non-empty")

        val reminders = parseTimes(args.reminders) ?: return err("Invalid reminders — use HH:MM")

        val specs = args.fields.map { f ->
            val fname = f.name.trim()
            if (fname.isBlank()) return err("Each field needs a non-blank 'name'")
            if (f.dailyTarget != null && (f.dailyTarget <= 0.0 || f.dailyTarget.isNaN() || f.dailyTarget.isInfinite())) {
                return err("'dailyTarget' must be > 0 (field '$fname')")
            }
            val direction = f.direction?.let {
                Direction.parse(it.lowercase()) ?: return err("Invalid direction '$it' (field '$fname') — use 'more' or 'less'")
            }
            HabitService.FieldSpec(
                name = fname.take(64),
                dailyTarget = f.dailyTarget,
                unit = f.unit?.trim()?.ifEmpty { null }?.take(16),
                direction = direction,
            )
        }

        val root = HabitService.addHabitGroup(
            userId = userId,
            name = name.take(64),
            fields = specs,
            reminders = reminders,
        )
        BotNotifier.notify(userId, Strings.mcpCreatedHabit(lang, root.name))
        return ok(McpJson.encodeToString(root))
    }

    private fun buildSchema(): ToolSchema {
        val props = buildJsonObject {
            putJsonObject("name") { put("type", "string") }
            putJsonObject("fields") {
                put("type", "array")
                put("minItems", 1)
                putJsonObject("items") {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("name") { put("type", "string") }
                        putJsonObject("dailyTarget") {
                            put("type", "number")
                            put("exclusiveMinimum", 0)
                        }
                        putJsonObject("unit") { put("type", "string") }
                        putJsonObject("direction") {
                            put("type", "string")
                            putJsonArray("enum") { add("more"); add("less") }
                        }
                    }
                    putJsonArray("required") { add("name") }
                }
            }
            putJsonObject("reminders") {
                put("type", "array")
                putJsonObject("items") {
                    put("type", "string")
                    put("pattern", "^[0-2][0-9]:[0-5][0-9]$")
                }
            }
        }
        return ToolSchema(properties = props, required = listOf("name", "fields"))
    }
}
