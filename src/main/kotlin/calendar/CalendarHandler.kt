package calendar

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import services.CalendarFeedService
import services.CalendarTokenService
import services.UserSettingsService
import java.time.ZoneOffset

/** Serves the read-only iCal feed at `/calendar/<token>(.ics)`. The token authenticates the user. */
object CalendarHandler {

    suspend fun handle(call: ApplicationCall) {
        val raw = call.parameters["token"]?.removeSuffix(".ics")
        val sub = CalendarTokenService.authenticate(raw)
        if (sub == null) {
            call.respond(HttpStatusCode.NotFound)
            return
        }
        val tz = UserSettingsService.getTimezone(sub.userId) ?: ZoneOffset.UTC
        val ics = CalendarFeedService.build(sub.userId, tz, sub.includeCheckins, sub.includeReminders)
        call.respondText(ics, ContentType.parse("text/calendar; charset=utf-8"))
    }
}
