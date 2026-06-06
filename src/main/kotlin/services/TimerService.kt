package services

import db.TimerRepository
import dto.HabitType
import dto.RunningTimer
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
        data class Stopped(val minutes: Double, val checkinId: Long) : StopOutcome
        data object NotRunning : StopOutcome
        data object NotFound : StopOutcome
    }

    fun start(habitId: Long, userId: Long): StartOutcome {
        val habit = HabitService.findById(habitId, userId) ?: return StartOutcome.NotFound
        if (habit.type != HabitType.TIMER) return StartOutcome.NotFound
        return if (TimerRepository.start(habitId, userId)) StartOutcome.Started else StartOutcome.AlreadyRunning
    }

    /** Stops a running timer and records the elapsed minutes as a [date] check-in. */
    fun stop(habitId: Long, userId: Long, date: LocalDate): StopOutcome {
        val habit = HabitService.findById(habitId, userId) ?: return StopOutcome.NotFound
        if (habit.type != HabitType.TIMER) return StopOutcome.NotFound
        val startedAt = TimerRepository.stop(habitId, userId) ?: return StopOutcome.NotRunning
        val minutes = elapsedMinutes(startedAt)
        val checkinId = CheckInService.recordTimer(habitId, userId, date, minutes)
        return StopOutcome.Stopped(minutes, checkinId)
    }

    fun running(userId: Long): List<RunningTimer> = TimerRepository.running(userId)

    fun find(habitId: Long, userId: Long): RunningTimer? = TimerRepository.find(habitId, userId)

    /** Minutes elapsed since [startedAt], rounded to two decimals (never below a recordable epsilon). */
    fun elapsedMinutes(startedAt: Instant): Double {
        val seconds = Duration.between(startedAt, Instant.now()).seconds.coerceAtLeast(1)
        return Math.round(seconds / 60.0 * 100.0) / 100.0
    }
}
