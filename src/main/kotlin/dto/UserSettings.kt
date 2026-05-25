package dto

import kotliquery.Row

data class UserSettings(
    val userId: Long,
    val timezone: String?,
    val language: String?
)

fun Row.toUserSettings(): UserSettings = UserSettings(
    userId = long("user_id"),
    timezone = stringOrNull("timezone"),
    language = stringOrNull("language")
)
