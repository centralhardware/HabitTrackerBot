import db.McpTokenRepository
import dto.McpToken
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

object McpTokenService {

    const val MAX_ACTIVE_TOKENS_PER_USER = 10
    private const val TOKEN_PREFIX = "mcp_"
    private const val RAW_BYTES = 32

    private val random = SecureRandom()
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()

    data class Issued(val token: McpToken, val plaintext: String)

    fun issue(userId: Long, label: String): Issued? {
        if (McpTokenRepository.countActive(userId) >= MAX_ACTIVE_TOKENS_PER_USER) return null
        val bytes = ByteArray(RAW_BYTES).also(random::nextBytes)
        val plaintext = TOKEN_PREFIX + encoder.encodeToString(bytes)
        val hash = sha256(plaintext.toByteArray(Charsets.UTF_8))
        val saved = McpTokenRepository.insert(userId, hash, label.take(64))
        return Issued(saved, plaintext)
    }

    fun listActive(userId: Long): List<McpToken> = McpTokenRepository.listActive(userId)

    fun revoke(id: Long, userId: Long): Boolean = McpTokenRepository.revoke(id, userId)

    fun authenticate(authorizationHeader: String?): Long? {
        val token = parseBearer(authorizationHeader) ?: return null
        if (!token.startsWith(TOKEN_PREFIX)) return null
        val hash = sha256(token.toByteArray(Charsets.UTF_8))
        val userId = McpTokenRepository.findActiveUserIdByHash(hash) ?: return null
        McpTokenRepository.touchLastUsed(hash)
        return userId
    }

    private fun parseBearer(header: String?): String? {
        if (header == null) return null
        val parts = header.trim().split(' ', limit = 2)
        if (parts.size != 2) return null
        if (!parts[0].equals("Bearer", ignoreCase = true)) return null
        return parts[1].trim().takeIf { it.isNotEmpty() }
    }

    private fun sha256(input: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(input)
}
