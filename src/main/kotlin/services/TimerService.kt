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
        val row = TimerRepository.stop(habitId, userId) ?: return StopOutcome.NotRunning
        val beforeValues = row.pendingValuesJson?.let {
            runCatching { Json.decodeFromString<Map<Long, String>>(it) }.getOrDefault(emptyMap())
        } ?: emptyMap()
        val stoppedAt = Instant.now()
        // The live segment only counts when not paused; everything earlier sits in accumulated_seconds.
        val liveSeconds = if (row.paused) 0.0 else Duration.between(row.startedAt, stoppedAt).seconds.toDouble()
        val totalSeconds = (row.accumulatedSeconds + liveSeconds).coerceAtLeast(1.0)
        // Split the live segment across local days; banked (paused) time lands on the segment's
        // first day, or on `today` when paused at stop with no live segment to attribute it to.
        val segments = if (zone != null && !row.paused) {
            val live = splitByLocalDay(row.startedAt, stoppedAt, zone).toMutableList()
            if (live.isEmpty()) live.add((LocalDate.now(zone)) to 0.0)
            if (row.accumulatedSeconds > 0) live[0] = live[0].first to (live[0].second + row.accumulatedSeconds)
            live
        } else {
            listOf(today to totalSeconds)
        }
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

    /** Pauses a running timer; false if it wasn't running or was already paused. */
    fun pause(habitId: Long, userId: Long): Boolean = TimerRepository.pause(habitId, userId)

    /** Resumes a paused timer; false if it wasn't running or wasn't paused. */
    fun resume(habitId: Long, userId: Long): Boolean = TimerRepository.resume(habitId, userId)

    fun running(userId: Long): List<RunningTimer> = TimerRepository.running(userId)

    fun find(habitId: Long, userId: Long): RunningTimer? = TimerRepository.find(habitId, userId)

    /** Remembers the message that shows this running timer, so the background ticker can update it. */
    fun setMessage(habitId: Long, userId: Long, messageId: Long) {
        TimerRepository.setMessage(habitId, userId, messageId)
    }

    /** Running timers (with a tracked message) the ticker should repaint. */
    fun dueTicks(): List<RunningTimerTick> = TimerRepository.dueTicks()

    /** Whole seconds a timer has run so far: banked time plus the live segment (frozen while paused). */
    fun elapsedSeconds(timer: RunningTimer): Double =
        elapsedSeconds(timer.startedAt, timer.accumulatedSeconds, timer.pausedAt)

    /** Whole seconds elapsed for the given start/accumulated/paused state (never below a recordable second). */
    fun elapsedSeconds(startedAt: Instant, accumulatedSeconds: Double = 0.0, pausedAt: Instant? = null): Double {
        val live = if (pausedAt != null) 0.0 else Duration.between(startedAt, Instant.now()).seconds.toDouble()
        return (accumulatedSeconds + live).coerceAtLeast(1.0)
    }
}
