package services

import db.CalendarTokenRepository
import dto.CalendarSubscription
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

object CalendarTokenService {

    private const val TOKEN_PREFIX = "cal_"
    private const val RAW_BYTES = 32

    private val random = SecureRandom()
    private val encoder = Base64.getUrlEncoder().withoutPadding()

    data class Issued(val subscription: CalendarSubscription, val token: String)

    /** Issues (or resets) the user's calendar token, returning the plaintext to embed in the URL. */
    fun issue(userId: Long): Issued {
        val bytes = ByteArray(RAW_BYTES).also(random::nextBytes)
        val token = TOKEN_PREFIX + encoder.encodeToString(bytes)
        val sub = CalendarTokenRepository.upsertToken(userId, sha256(token))
        return Issued(sub, token)
    }

    fun get(userId: Long): CalendarSubscription? = CalendarTokenRepository.find(userId)

    fun setContent(userId: Long, includeCheckins: Boolean, includeReminders: Boolean): Boolean =
        CalendarTokenRepository.updateContent(userId, includeCheckins, includeReminders)

    fun revoke(userId: Long): Boolean = CalendarTokenRepository.delete(userId)

    /** Resolves a feed token (the bare path segment) to its subscription, touching last-used. */
    fun authenticate(token: String?): CalendarSubscription? {
        if (token == null || !token.startsWith(TOKEN_PREFIX)) return null
        val hash = sha256(token)
        val sub = CalendarTokenRepository.findByHash(hash) ?: return null
        CalendarTokenRepository.touchLastUsed(hash)
        return sub
    }

    private fun sha256(input: String): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
}
