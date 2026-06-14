object Config {

    val DATABASE_URL: String = System.getenv("DATABASE_URL")
        ?: error("DATABASE_URL env var is required (e.g. jdbc:postgresql://localhost:5432/habits?user=foo&password=bar)")

    val MCP_HOST: String = System.getenv("MCP_HOST") ?: "127.0.0.1"
    val MCP_PORT: Int = System.getenv("MCP_PORT")?.toIntOrNull() ?: 7173
    // Single public base URL of this server; the /mcp and /calendar paths are derived from it.
    val PUBLIC_URL: String = (System.getenv("PUBLIC_URL") ?: "https://<your-host>").removeSuffix("/")

    val MCP_PUBLIC_URL: String = "$PUBLIC_URL/mcp"
    val CALENDAR_PUBLIC_URL: String = "$PUBLIC_URL/calendar"
}
