import dev.inmo.tgbotapi.abstracts.OptionallyFromUser
import dev.inmo.tgbotapi.types.chat.User
import dev.inmo.tgbotapi.types.message.abstracts.AccessibleMessage

fun AccessibleMessage.senderUser(): User? = (this as? OptionallyFromUser)?.from

fun AccessibleMessage.senderUserId(): Long? = senderUser()?.id?.chatId?.long

fun AccessibleMessage.senderLang(): Lang {
    val user = senderUser()
    val detected = Lang.of(user)
    val id = user?.id?.chatId?.long ?: return detected
    UserSettingsService.touchLanguage(id, detected)
    return UserSettingsService.getLanguage(id) ?: detected
}
