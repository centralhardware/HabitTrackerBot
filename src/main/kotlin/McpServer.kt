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
import io.ktor.util.AttributeKey
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.mcpStatelessStreamableHttp
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import mcp.CheckinRecordTool
import mcp.CheckinsListTool
import mcp.HabitsListTool
import mcp.McpTool
import mcp.QuantityGroupRecordTool
import mcp.StatsUserTool

private val UserIdKey = AttributeKey<Long>("mcpUserId")
private val LangKey = AttributeKey<Lang>("mcpLang")

private val tools: List<McpTool> = listOf(
    HabitsListTool,
    CheckinRecordTool,
    QuantityGroupRecordTool,
    CheckinsListTool,
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
                context.attributes.put(LangKey, UserSettingsService.getLanguage(userId) ?: Lang.EN)
            }
            mcpStatelessStreamableHttp(
                path = "/mcp",
                enableDnsRebindingProtection = false,
            ) {
                val userId = call.attributes[UserIdKey]
                val lang = call.attributes[LangKey]
                buildServer(userId, lang)
            }
        }
        return app.start(wait = false)
    }

    private fun buildServer(userId: Long, lang: Lang): Server {
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
            ) { request -> tool.handle(userId, lang, request) }
        }
        return server
    }
}
