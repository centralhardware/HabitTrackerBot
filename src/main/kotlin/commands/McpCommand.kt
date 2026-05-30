package commands

import Config
import services.McpTokenService
import Strings
import services.UserSettingsService
import dev.inmo.tgbotapi.extensions.api.send.sendMessage
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContext
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onCommand
import dev.inmo.tgbotapi.utils.buildEntities
import dev.inmo.tgbotapi.utils.code
import senderLang
import senderUserId
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

fun BehaviourContext.registerMcpCommands() {
    onCommand("mcp_new") { message ->
        val userId = message.senderUserId() ?: return@onCommand
        val lang = message.senderLang()
        val label = commandArgs(message.content.text).take(64).ifBlank { "default" }

        val issued = McpTokenService.issue(userId, label)
        if (issued == null) {
            sendMessage(
                message.chat.id,
                Strings.mcpTokenLimitReached(lang, McpTokenService.MAX_ACTIVE_TOKENS_PER_USER)
            )
            return@onCommand
        }

        sendMessage(
            message.chat.id,
            buildEntities("") {
                +Strings.mcpTokenIssuedPrefix(lang, issued.token.label, Config.MCP_PUBLIC_URL)
                +"\n\n"
                code(issued.plaintext)
                +"\n\n"
                +Strings.mcpTokenIssuedSuffix(lang)
            }
        )
    }

    onCommand("mcp_list") { message ->
        val userId = message.senderUserId() ?: return@onCommand
        val lang = message.senderLang()
        val tokens = McpTokenService.listActive(userId)
        if (tokens.isEmpty()) {
            sendMessage(message.chat.id, Strings.mcpNoTokens(lang))
            return@onCommand
        }
        val tz: ZoneId = UserSettingsService.getTimezone(userId) ?: ZoneOffset.UTC
        val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(tz)
        val body = buildString {
            appendLine(Strings.mcpTokensHeader(lang))
            tokens.forEach { t ->
                val used = t.lastUsedAt?.let { fmt.format(it) } ?: Strings.mcpNeverUsed(lang)
                appendLine("• #${t.id} «${t.label}»")
                appendLine("    ${Strings.mcpCreatedAt(lang)}: ${fmt.format(t.createdAt)}")
                appendLine("    ${Strings.mcpLastUsed(lang)}: $used")
            }
        }.trimEnd()
        sendMessage(message.chat.id, body)
    }

    onCommand("mcp_revoke") { message ->
        val userId = message.senderUserId() ?: return@onCommand
        val lang = message.senderLang()
        val id = commandArgs(message.content.text).toLongOrNull()
        if (id == null) {
            sendMessage(message.chat.id, Strings.mcpRevokeUsage(lang))
            return@onCommand
        }
        val ok = McpTokenService.revoke(id, userId)
        sendMessage(
            message.chat.id,
            if (ok) Strings.mcpTokenRevoked(lang, id) else Strings.mcpTokenNotFound(lang, id)
        )
    }
}

private fun commandArgs(text: String): String {
    val space = text.indexOf(' ')
    return if (space == -1) "" else text.substring(space + 1).trim()
}
