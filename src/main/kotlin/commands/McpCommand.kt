package commands

import Config
import services.McpTokenService
import Strings
import lang
import tz
import userId
import dev.inmo.tgbotapi.extensions.api.send.sendMessage
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContext
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onCommand
import dev.inmo.tgbotapi.utils.buildEntities
import dev.inmo.tgbotapi.utils.code
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

fun BehaviourContext.registerMcpCommands() {
    onCommand("mcp_new") { message ->
        val label = commandArgs(message.content.text).take(64).ifBlank { "default" }

        val issued = McpTokenService.issue(data.userId, label)
        if (issued == null) {
            sendMessage(
                message.chat.id,
                Strings.mcpTokenLimitReached(data.lang, McpTokenService.MAX_ACTIVE_TOKENS_PER_USER)
            )
            return@onCommand
        }

        sendMessage(
            message.chat.id,
            buildEntities("") {
                +Strings.mcpTokenIssuedPrefix(data.lang, issued.token.label, Config.MCP_PUBLIC_URL)
                +"\n\n"
                code(issued.plaintext)
                +"\n\n"
                +Strings.mcpTokenIssuedSuffix(data.lang)
            }
        )
    }

    onCommand("mcp_list") { message ->
        val tokens = McpTokenService.listActive(data.userId)
        if (tokens.isEmpty()) {
            sendMessage(message.chat.id, Strings.mcpNoTokens(data.lang))
            return@onCommand
        }
        val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(data.tz ?: ZoneOffset.UTC)
        val body = buildString {
            appendLine(Strings.mcpTokensHeader(data.lang))
            tokens.forEach { t ->
                val used = t.lastUsedAt?.let { fmt.format(it) } ?: Strings.mcpNeverUsed(data.lang)
                appendLine("• #${t.id} «${t.label}»")
                appendLine("    ${Strings.mcpCreatedAt(data.lang)}: ${fmt.format(t.createdAt)}")
                appendLine("    ${Strings.mcpLastUsed(data.lang)}: $used")
            }
        }.trimEnd()
        sendMessage(message.chat.id, body)
    }

    onCommand("mcp_revoke") { message ->
        val id = commandArgs(message.content.text).toLongOrNull()
        if (id == null) {
            sendMessage(message.chat.id, Strings.mcpRevokeUsage(data.lang))
            return@onCommand
        }
        val ok = McpTokenService.revoke(id, data.userId)
        sendMessage(
            message.chat.id,
            if (ok) Strings.mcpTokenRevoked(data.lang, id) else Strings.mcpTokenNotFound(data.lang, id)
        )
    }
}

private fun commandArgs(text: String): String {
    val space = text.indexOf(' ')
    return if (space == -1) "" else text.substring(space + 1).trim()
}
