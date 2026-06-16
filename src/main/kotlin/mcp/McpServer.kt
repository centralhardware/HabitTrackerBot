package mcp

import Lang
import dev.inmo.kslog.common.KSLog
import dev.inmo.kslog.common.error
import dev.inmo.kslog.common.info
import dev.inmo.kslog.common.warning
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.header
import io.ktor.server.request.path
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import calendar.CalendarHandler
import io.ktor.util.AttributeKey
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.mcpStatelessStreamableHttp
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import services.McpTokenService
import services.UserSettingsService
import java.time.ZoneId
import java.time.ZoneOffset

private val UserIdKey = AttributeKey<Long>("mcpUserId")
private val LangKey = AttributeKey<Lang>("mcpLang")
private val TzKey = AttributeKey<ZoneId>("mcpTz")

private val tools: List<McpTool> = listOf(
    HabitsListTool,
    QuantityRecordTool,
    CheckRecordTool,
    CheckinsListTool,
    CheckinUpdateTool,
    CheckinDeleteTool,
    ParamValuesMergeTool,
    StatsUserTool,
)

object McpServer {

    fun start(): EmbeddedServer<*, *> {
        val app = embeddedServer(CIO, host = Config.MCP_HOST, port = Config.MCP_PORT) {
            intercept(ApplicationCallPipeline.Plugins) {
                if (!context.request.path().startsWith("/mcp")) return@intercept
                val userId = McpTokenService.authenticate(context.request.header(HttpHeaders.Authorization))
                if (userId == null) {
                    context.response.header(HttpHeaders.WWWAuthenticate, "Bearer realm=\"mcp\"")
                    context.respond(HttpStatusCode.Unauthorized)
                    finish()
                    return@intercept
                }
                context.attributes.put(UserIdKey, userId)
                context.attributes.put(LangKey, Lang.stored(UserSettingsService.getLanguageCode(userId)) ?: Lang.EN)
                context.attributes.put(TzKey, UserSettingsService.getTimezone(userId) ?: ZoneOffset.UTC)
            }
            routing {
                // Read-only iCal subscription feed; the token in the path authenticates the user,
                // so calendar apps (Google/Apple/Outlook) can subscribe without an auth header.
                get("/calendar/{token}") { CalendarHandler.handle(call) }
            }
            mcpStatelessStreamableHttp(
                path = "/mcp",
                enableDnsRebindingProtection = false,
            ) {
                val userId = call.attributes[UserIdKey]
                val lang = call.attributes[LangKey]
                val tz = call.attributes[TzKey]
                buildServer(userId, lang, tz)
            }
        }
        return app.start(wait = false)
    }

    private fun buildServer(userId: Long, lang: Lang, tz: ZoneId): Server {
        val server = Server(
            serverInfo = Implementation(name = "habit-tracker", version = "1.0.0"),
            options = ServerOptions(
                capabilities = ServerCapabilities(
                    tools = ServerCapabilities.Tools(listChanged = false),
                ),
            ),
        )
        tools.forEach { tool ->
            server.addTool(
                name = tool.name,
                description = tool.description,
                inputSchema = tool.inputSchema,
                toolAnnotations = tool.annotations,
            ) { request -> invokeWithLogging(tool, userId, lang, tz, request) }
        }
        return server
    }
}

private fun invokeWithLogging(tool: McpTool, userId: Long, lang: Lang, tz: ZoneId, request: CallToolRequest): CallToolResult {
    val args = request.arguments ?: "{}"
    KSLog.info("mcp call user=$userId tool=${tool.name} args=$args")
    return try {
        val result = tool.handle(userId, lang, tz, request)
        if (result.isError == true) {
            val reason = (result.content.firstOrNull() as? TextContent)?.text ?: ""
            KSLog.warning("mcp fail user=$userId tool=${tool.name} args=$args reason=$reason")
        }
        result
    } catch (e: Throwable) {
        KSLog.error("mcp crash user=$userId tool=${tool.name} args=$args", e)
        CallToolResult(content = listOf(TextContent("Internal error")), isError = true)
    }
}
