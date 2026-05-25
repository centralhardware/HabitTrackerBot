import db.UserSettingsRepository
import dto.UserSettings
import java.time.ZoneId

object UserSettingsService {

    fun getTimezone(userId: Long): ZoneId? =
        UserSettingsRepository.find(userId)?.timezone?.let { runCatching { ZoneId.of(it) }.getOrNull() }

    fun setTimezone(userId: Long, tz: ZoneId) {
        UserSettingsRepository.upsert(current(userId).copy(timezone = tz.id))
    }

    fun getLanguage(userId: Long): Lang? =
        UserSettingsRepository.find(userId)?.language?.let { runCatching { Lang.valueOf(it) }.getOrNull() }

    fun setLanguage(userId: Long, lang: Lang) {
        UserSettingsRepository.upsert(current(userId).copy(language = lang.name))
    }

    fun touchLanguage(userId: Long, lang: Lang) {
        val current = current(userId)
        if (current.language == null) {
            UserSettingsRepository.upsert(current.copy(language = lang.name))
        }
    }

    private fun current(userId: Long): UserSettings =
        UserSettingsRepository.find(userId) ?: UserSettings(userId, timezone = null, language = null)
}
