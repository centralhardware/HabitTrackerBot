package services

import db.TimerRepository
import dto.HabitType
import dto.RunningTimer
import dto.RunningTimerTick
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

object TimerService {

    sealed interface StartOutcome {
        data object Started : StartOutcome
        data object AlreadyRunning : StartOutcome
        data object NotFound : StartOutcome
    }

    sealed interface StopOutcome {
        /** [beforeValues] are the "before"-phase field values stashed at start (paramId → text). */
        data class Stopped(val seconds: Double, val checkinId: Long, val beforeValues: Map<Long, String>) : StopOutcome
        data object NotRunning : StopOutcome
        data object NotFound : StopOutcome
    }

    /** [beforeValues] are the "before"-phase annotation fields the user filled in (paramId → text). */
    fun start(habitId: Long, userId: Long, beforeValues: Map<Long, String> = emptyMap()): StartOutcome {
        val habit = HabitService.findById(habitId, userId) ?: return StartOutcome.NotFound
        if (habit.type != HabitType.TIMER) return StartOutcome.NotFound
        val json = if (beforeValues.isEmpty()) null else Json.encodeToString(beforeValues)
        return if (TimerRepository.start(habitId, userId, json)) StartOutcome.Started else StartOutcome.AlreadyRunning
    }

    /**
     * Stops a running timer and records the elapsed seconds as a check-in. A timer that ran across
     * midnight is split into one check-in per local day (in [zone]) so each day shows only the time
     * actually spent in it. [today] is the fallback day used when no timezone is known.
     * Returns the total elapsed seconds and the id of the check-in on the final (most recent) day,
     * so a follow-up comment can be attached to it.
     */
    fun stop(habitId: Long, userId: Long, today: LocalDate, zone: ZoneId?): StopOutcome {
        val habit = HabitService.findById(habitId, userId) ?: return StopOutcome.NotFound
        if (habit.type != HabitType.TIMER) return StopOutcome.NotFound
        val (startedAt, pendingJson) = TimerRepository.stop(habitId, userId) ?: return StopOutcome.NotRunning
        val beforeValues = pendingJson?.let {
            runCatching { Json.decodeFromString<Map<Long, String>>(it) }.getOrDefault(emptyMap())
        } ?: emptyMap()
        val stoppedAt = Instant.now()
        val totalSeconds = Duration.between(startedAt, stoppedAt).seconds.coerceAtLeast(1).toDouble()
        val segments = if (zone != null) splitByLocalDay(startedAt, stoppedAt, zone) else listOf(today to totalSeconds)
        var lastCheckinId = 0L
        for ((day, seconds) in segments) {
            val id = CheckInService.recordTimer(habitId, userId, day, seconds)
            if (id > 0) lastCheckinId = id
        }
        // Never lose a sub-second timer: record at least one second on the final day.
        if (lastCheckinId == 0L) {
            lastCheckinId = CheckInService.recordTimer(habitId, userId, segments.lastOrNull()?.first ?: today, 1.0)
        }
        return StopOutcome.Stopped(totalSeconds, lastCheckinId, beforeValues)
    }

    /** Splits the [start, end] interval into the whole seconds falling within each local day of [zone]. */
    private fun splitByLocalDay(start: Instant, end: Instant, zone: ZoneId): List<Pair<LocalDate, Double>> {
        val segments = mutableListOf<Pair<LocalDate, Double>>()
        var cursor = start
        while (cursor.isBefore(end)) {
            val day = cursor.atZone(zone).toLocalDate()
            val nextMidnight = day.plusDays(1).atStartOfDay(zone).toInstant()
            val segmentEnd = if (nextMidnight.isBefore(end)) nextMidnight else end
            segments.add(day to Duration.between(cursor, segmentEnd).seconds.toDouble())
            cursor = segmentEnd
        }
        return segments
    }

    fun running(userId: Long): List<RunningTimer> = TimerRepository.running(userId)

    fun find(habitId: Long, userId: Long): RunningTimer? = TimerRepository.find(habitId, userId)

    /** Remembers the message that shows this running timer, so the background ticker can update it. */
    fun setMessage(habitId: Long, userId: Long, messageId: Long) {
        TimerRepository.setMessage(habitId, userId, messageId)
    }

    /** Running timers (with a tracked message) the ticker should repaint. */
    fun dueTicks(): List<RunningTimerTick> = TimerRepository.dueTicks()

    /** Whole seconds elapsed since [startedAt] (never below a recordable second). */
    fun elapsedSeconds(startedAt: Instant): Double =
        Duration.between(startedAt, Instant.now()).seconds.coerceAtLeast(1).toDouble()
}
