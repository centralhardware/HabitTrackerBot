package mcp

import BotNotifier
import Lang
import UserSettingsService
import dev.inmo.kslog.common.KSLog
import dev.inmo.kslog.common.error
import dev.inmo.kslog.common.info
import dev.inmo.kslog.common.warning
import dto.McpJson
import dto.McpProp
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject

internal fun ok(text: String): CallToolResult =
    CallToolResult(content = listOf(TextContent(text)), isError = false)

internal fun err(text: String): CallToolResult =
    CallToolResult(content = listOf(TextContent(text)), isError = true)

internal fun logCall(userId: Long, tool: String, args: JsonObject?) {
    KSLog.info("mcp call user=$userId tool=$tool args=${args ?: "{}"}")
}

internal fun failed(userId: Long, tool: String, args: JsonObject?, reason: String): CallToolResult {
    KSLog.warning("mcp fail user=$userId tool=$tool args=${args ?: "{}"} reason=$reason")
    return err(reason)
}

internal fun crashed(userId: Long, tool: String, args: JsonObject?, e: Throwable): CallToolResult {
    KSLog.error("mcp crash user=$userId tool=$tool args=${args ?: "{}"}", e)
    return err("Internal error")
}

internal inline fun notifyUser(userId: Long, text: (Lang) -> String) {
    val lang = UserSettingsService.getLanguage(userId) ?: Lang.EN
    BotNotifier.notify(userId, text(lang))
}

internal fun emptyObjectSchema(): ToolSchema = ToolSchema(properties = JsonObject(emptyMap()))

internal fun toolSchema(props: Map<String, McpProp>, required: List<String> = emptyList()): ToolSchema =
    ToolSchema(
        properties = McpJson.encodeToJsonElement(props).jsonObject,
        required = required,
    )
