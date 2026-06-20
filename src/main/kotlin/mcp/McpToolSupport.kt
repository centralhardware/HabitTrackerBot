package mcp

import Lang
import dto.McpJson
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.JsonObject
import java.time.LocalDate
import java.time.ZoneId


abstract class TypedMcpTool<A>(
    private val deserializer: DeserializationStrategy<A>,
) : McpTool {
    final override fun handle(userId: Long, lang: Lang, tz: ZoneId, request: CallToolRequest): CallToolResult {
        val rawArgs = request.arguments ?: return err("arguments required")
        val args = runCatching { McpJson.decodeFromJsonElement(deserializer, rawArgs) }
            .getOrElse { return err("Invalid arguments: ${it.message}") }
        return handle(userId, lang, tz, args)
    }

    abstract fun handle(userId: Long, lang: Lang, tz: ZoneId, args: A): CallToolResult
}

internal fun ok(text: String): CallToolResult =
    CallToolResult(content = listOf(TextContent(text)), isError = false)

internal fun err(text: String): CallToolResult =
    CallToolResult(content = listOf(TextContent(text)), isError = true)

internal fun emptyObjectSchema(): ToolSchema = ToolSchema(properties = JsonObject(emptyMap()))

/** Human-readable length of the edit/delete window, for tool descriptions and error messages. */
internal const val CHECKIN_EDIT_WINDOW = "month"

/** Oldest date (inclusive) whose check-ins may still be edited or deleted: today back through one month ago. */
internal fun checkinEditCutoff(tz: ZoneId): LocalDate = LocalDate.now(tz).minusMonths(1)
