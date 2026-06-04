import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContextData
import dev.inmo.tgbotapi.extensions.behaviour_builder.buildSubcontextInitialAction
import dev.inmo.tgbotapi.extensions.utils.extensions.sourceUser
import services.UserSettingsService
import java.time.ZoneId

var BehaviourContextData.userId: Long
    get() = get("userId") as Long
    set(value) = set("userId", value)

var BehaviourContextData.lang: Lang
    get() = get("lang") as Lang
    set(value) = set("lang", value)

var BehaviourContextData.tz: ZoneId?
    get() = get("tz") as ZoneId?
    set(value) = set("tz", value)

val populateUserContext = buildSubcontextInitialAction {
    add { update ->
        val user = update.sourceUser() ?: return@add
        val id = user.id.chatId.long
        val detected = Lang.of(user)
        UserSettingsService.touchLanguageCode(id, detected.name)
        data.userId = id
        data.lang = Lang.stored(UserSettingsService.getLanguageCode(id)) ?: detected
        data.tz = UserSettingsService.getTimezone(id)
    }
}
