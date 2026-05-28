package mcp

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

internal fun emptyObjectSchema(): ToolSchema = ToolSchema(properties = JsonObject(emptyMap()))

internal fun toolSchema(props: Map<String, McpProp>, required: List<String> = emptyList()): ToolSchema =
    ToolSchema(
        properties = McpJson.encodeToJsonElement(props).jsonObject,
        required = required,
    )
