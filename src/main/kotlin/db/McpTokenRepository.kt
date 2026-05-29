package db

import DatabaseService
import dto.McpToken
import dto.toMcpToken
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using

object McpTokenRepository {

    fun insert(userId: Long, tokenHash: ByteArray, label: String): McpToken {
        return using(sessionOf(DatabaseService.dataSource, returnGeneratedKey = true)) { session ->
            val id = session.updateAndReturnGeneratedKey(
                queryOf(
                    """
                    INSERT INTO mcp_tokens (user_id, token_hash, label)
                    VALUES (?, ?, ?)
                    """.trimIndent(),
                    userId, tokenHash, label
                )
            ) ?: error("Failed to insert MCP token")
            find(id) ?: error("Inserted MCP token not found")
        }
    }

    fun listActive(userId: Long): List<McpToken> =
        sessionOf(DatabaseService.dataSource).use { session ->
            session.run(
                queryOf(
                    """
                    SELECT id, user_id, label, created_at, last_used_at
                    FROM mcp_tokens
                    WHERE user_id = ? AND revoked_at IS NULL
                    ORDER BY created_at
                    """.trimIndent(),
                    userId
                ).map { it.toMcpToken() }.asList
            )
        }

    fun revoke(id: Long, userId: Long): Boolean =
        sessionOf(DatabaseService.dataSource).use { session ->
            session.update(
                queryOf(
                    """
                    UPDATE mcp_tokens
                    SET revoked_at = now()
                    WHERE id = ? AND user_id = ? AND revoked_at IS NULL
                    """.trimIndent(),
                    id, userId
                )
            ) > 0
        }

    fun findActiveByHash(tokenHash: ByteArray): McpToken? =
        sessionOf(DatabaseService.dataSource).use { session ->
            session.run(
                queryOf(
                    """
                    SELECT id, user_id, label, created_at, last_used_at
                    FROM mcp_tokens
                    WHERE token_hash = ? AND revoked_at IS NULL
                    """.trimIndent(),
                    tokenHash
                ).map { it.toMcpToken() }.asSingle
            )
        }

    fun touchLastUsed(tokenHash: ByteArray) {
        sessionOf(DatabaseService.dataSource).use { session ->
            session.update(
                queryOf(
                    "UPDATE mcp_tokens SET last_used_at = now() WHERE token_hash = ?",
                    tokenHash
                )
            )
        }
    }

    private fun find(id: Long): McpToken? =
        sessionOf(DatabaseService.dataSource).use { session ->
            session.run(
                queryOf(
                    "SELECT id, user_id, label, created_at, last_used_at FROM mcp_tokens WHERE id = ?",
                    id
                ).map { it.toMcpToken() }.asSingle
            )
        }
}
