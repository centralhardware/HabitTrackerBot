package dto

import kotliquery.Row
import java.time.Instant

data class McpToken(
    val id: Long,
    val userId: Long,
    val label: String,
    val createdAt: Instant,
    val lastUsedAt: Instant?,
)

fun Row.toMcpToken(): McpToken = McpToken(
    id = long("id"),
    userId = long("user_id"),
    label = string("label"),
    createdAt = instant("created_at"),
    lastUsedAt = instantOrNull("last_used_at"),
)
