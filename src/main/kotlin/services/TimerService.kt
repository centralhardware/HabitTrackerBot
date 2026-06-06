package services

import db.TimerRepository
import dto.HabitType
import dto.RunningTimer
import dto.RunningTimerTick
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

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

    /** Stops a running timer and records the elapsed seconds as a [date] check-in. */
    fun stop(habitId: Long, userId: Long, date: LocalDate): StopOutcome {
        val habit = HabitService.findById(habitId, userId) ?: return StopOutcome.NotFound
        if (habit.type != HabitType.TIMER) return StopOutcome.NotFound
        val startedAt = TimerRepository.stop(habitId, userId) ?: return StopOutcome.NotRunning
        val seconds = elapsedSeconds(startedAt)
        val checkinId = CheckInService.recordTimer(habitId, userId, date, seconds)
        return StopOutcome.Stopped(seconds, checkinId)
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
