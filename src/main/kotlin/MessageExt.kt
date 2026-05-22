import dev.inmo.tgbotapi.abstracts.OptionallyFromUser
import dev.inmo.tgbotapi.types.chat.User
import dev.inmo.tgbotapi.types.message.abstracts.AccessibleMessage

fun AccessibleMessage.senderUser(): User? = (this as? OptionallyFromUser)?.from

fun AccessibleMessage.senderUserId(): Long? = senderUser()?.id?.chatId?.long
