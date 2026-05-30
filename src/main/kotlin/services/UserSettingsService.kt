package services

import db.UserSettingsRepository
import dto.UserSettings
import java.time.ZoneId

object UserSettingsService {

    fun getTimezone(userId: Long): ZoneId? =
        UserSettingsRepository.find(userId)?.timezone?.let { runCatching { ZoneId.of(it) }.getOrNull() }

    fun setTimezone(userId: Long, tz: ZoneId) {
        UserSettingsRepository.upsert(current(userId).copy(timezone = tz.id))
    }

    fun getLanguageCode(userId: Long): String? =
        UserSettingsRepository.find(userId)?.language

    fun setLanguageCode(userId: Long, code: String) {
        UserSettingsRepository.upsert(current(userId).copy(language = code))
    }

    fun touchLanguageCode(userId: Long, code: String) {
        val current = current(userId)
        if (current.language == null) {
            UserSettingsRepository.upsert(current.copy(language = code))
        }
    }

    private fun current(userId: Long): UserSettings =
        UserSettingsRepository.find(userId) ?: UserSettings(userId, timezone = null, language = null)
}
