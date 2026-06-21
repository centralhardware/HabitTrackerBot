package services

import db.TrackRepository
import dto.DueReminder
import dto.Track
import dto.TrackStatus
import dto.TrackType
import dto.ResumedTrack
import java.time.Instant
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset

object TrackService {

    fun addTrack(track: Track): Track = TrackRepository.upsert(
        track.copy(
            id = 0L,
            status = TrackStatus.ACTIVE,
            reminders = track.reminders.sortedBy { it.offsetMinutes },
        )
    )

    fun listActive(userId: Long): List<Track> = TrackRepository.listActive(userId)

    fun findById(trackId: Long, userId: Long): Track? = TrackRepository.find(trackId, userId)

    fun softDelete(trackId: Long, userId: Long): Boolean = transition(trackId, userId, TrackStatus.DELETED)

    /** Soft-deletes a track field/param: hidden from the active track, kept for historical records.
     *  False if it's the track's only live param. */
    fun deleteParam(paramId: Long, userId: Long): Boolean = TrackRepository.deleteParam(paramId, userId)

    /**
     * Pauses a track for [durationDays] (0 = indefinitely, until a manual /resume). A finite
     * duration sets an auto-resume deadline that [autoResumeExpired] later lifts.
     */
    fun pause(trackId: Long, userId: Long, durationDays: Int): Boolean {
        val until = if (durationDays > 0)
            OffsetDateTime.now(ZoneOffset.UTC).plusDays(durationDays.toLong())
        else null
        return TrackRepository.pauseTrack(trackId, userId, until)
    }

    fun resume(trackId: Long, userId: Long): Boolean =
        transition(trackId, userId, TrackStatus.ACTIVE) { it.status == TrackStatus.PAUSED }

    /** Resumes every paused track whose deadline has passed, returning them so owners can be notified. */
    fun autoResumeExpired(): List<ResumedTrack> = TrackRepository.autoResumeExpired()

    private fun transition(
        trackId: Long,
        userId: Long,
        to: TrackStatus,
        allow: (Track) -> Boolean = { true }
    ): Boolean {
        val track = TrackRepository.find(trackId, userId) ?: return false
        if (!allow(track)) return false
        // Params cascade with the track row; flipping the track's status is enough.
        TrackRepository.upsert(track.copy(status = to))
        return true
    }

    fun findDue(): List<DueReminder> {
        val now = Instant.now()
        return TrackRepository.findRawDue().mapNotNull { r ->
            val tz = runCatching { ZoneId.of(r.tzId) }.getOrNull() ?: return@mapNotNull null
            val zdt = now.atZone(tz)
            val localMinute = zdt.toLocalTime().withSecond(0).withNano(0)
            val reminderLocalTime = LocalTime.ofSecondOfDay((r.offsetMinutes % 1440).toLong() * 60)
            val nextDay = r.offsetMinutes >= 1440
            if (localMinute != reminderLocalTime) return@mapNotNull null
            val trackDow = if (nextDay) zdt.dayOfWeek.minus(1) else zdt.dayOfWeek
            val trackDate = if (nextDay) zdt.toLocalDate().minusDays(1) else zdt.toLocalDate()
            if (r.reminderDays.isNotEmpty() && trackDow.value !in r.reminderDays) return@mapNotNull null
            DueReminder(
                reminderId = r.reminderId,
                trackId = r.trackId,
                trackType = r.trackType,
                userId = r.userId,
                name = r.name,
                offsetMinutes = r.offsetMinutes,
                userDate = trackDate,
                langCode = r.langCode,
            )
        }
    }

    fun backfillMissedScheduled(): List<DueReminder> {
        return TrackRepository.backfillMissedScheduled().map { r ->
            DueReminder(
                reminderId = r.reminderId,
                trackId = r.trackId,
                trackType = TrackType.CHECK,
                userId = r.userId,
                name = r.name,
                offsetMinutes = r.offsetMinutes,
                userDate = r.missedDate,
                langCode = r.langCode,
            )
        }
    }
}
