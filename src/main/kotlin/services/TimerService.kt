package services

import db.TimerRepository
import dto.HabitType
import dto.RunningTimer
import dto.RunningTimerTick
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
        data class Stopped(val seconds: Double, val checkinId: Long) : StopOutcome
        data object NotRunning : StopOutcome
        data object NotFound : StopOutcome
    }

    fun start(habitId: Long, userId: Long): StartOutcome {
        val habit = HabitService.findById(habitId, userId) ?: return StartOutcome.NotFound
        if (habit.type != HabitType.TIMER) return StartOutcome.NotFound
        return if (TimerRepository.start(habitId, userId)) StartOutcome.Started else StartOutcome.AlreadyRunning
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
        val startedAt = TimerRepository.stop(habitId, userId) ?: return StopOutcome.NotRunning
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
        return StopOutcome.Stopped(totalSeconds, lastCheckinId)
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
