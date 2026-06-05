package services

import db.HabitRepository
import dto.DueReminder
import dto.Habit
import dto.HabitStatus
import dto.HabitType
import dto.ResumedHabit
import java.time.Instant
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset

object HabitService {

    fun addHabit(habit: Habit): Habit = HabitRepository.upsert(
        habit.copy(
            id = 0L,
            status = HabitStatus.ACTIVE,
            reminders = habit.reminders.sortedBy { it.offsetMinutes },
        )
    )

    fun listActive(userId: Long): List<Habit> = HabitRepository.listActive(userId)

    fun findById(habitId: Long, userId: Long): Habit? = HabitRepository.find(habitId, userId)

    fun softDelete(habitId: Long, userId: Long): Boolean = transition(habitId, userId, HabitStatus.DELETED)

    /**
     * Pauses a habit for [durationDays] (0 = indefinitely, until a manual /resume). A finite
     * duration sets an auto-resume deadline that [autoResumeExpired] later lifts.
     */
    fun pause(habitId: Long, userId: Long, durationDays: Int): Boolean {
        val until = if (durationDays > 0)
            OffsetDateTime.now(ZoneOffset.UTC).plusDays(durationDays.toLong())
        else null
        return HabitRepository.pauseHabit(habitId, userId, until)
    }

    fun resume(habitId: Long, userId: Long): Boolean =
        transition(habitId, userId, HabitStatus.ACTIVE) { it.status == HabitStatus.PAUSED }

    /** Resumes every paused habit whose deadline has passed, returning them so owners can be notified. */
    fun autoResumeExpired(): List<ResumedHabit> = HabitRepository.autoResumeExpired()

    private fun transition(
        habitId: Long,
        userId: Long,
        to: HabitStatus,
        allow: (Habit) -> Boolean = { true }
    ): Boolean {
        val habit = HabitRepository.find(habitId, userId) ?: return false
        if (!allow(habit)) return false
        // Params cascade with the habit row; flipping the habit's status is enough.
        HabitRepository.upsert(habit.copy(status = to))
        return true
    }

    fun findDue(): List<DueReminder> {
        val now = Instant.now()
        return HabitRepository.findRawDue().mapNotNull { r ->
            val tz = runCatching { ZoneId.of(r.tzId) }.getOrNull() ?: return@mapNotNull null
            val zdt = now.atZone(tz)
            val localMinute = zdt.toLocalTime().withSecond(0).withNano(0)
            val reminderLocalTime = LocalTime.ofSecondOfDay((r.offsetMinutes % 1440).toLong() * 60)
            val nextDay = r.offsetMinutes >= 1440
            if (localMinute != reminderLocalTime) return@mapNotNull null
            val habitDow = if (nextDay) zdt.dayOfWeek.minus(1) else zdt.dayOfWeek
            val habitDate = if (nextDay) zdt.toLocalDate().minusDays(1) else zdt.toLocalDate()
            if (r.reminderDays.isNotEmpty() && habitDow.value !in r.reminderDays) return@mapNotNull null
            DueReminder(
                reminderId = r.reminderId,
                habitId = r.habitId,
                habitType = r.habitType,
                userId = r.userId,
                name = r.name,
                offsetMinutes = r.offsetMinutes,
                userDate = habitDate,
                langCode = r.langCode,
            )
        }
    }

    fun backfillMissedScheduled(): List<DueReminder> {
        return HabitRepository.backfillMissedScheduled().map { r ->
            DueReminder(
                reminderId = r.reminderId,
                habitId = r.habitId,
                habitType = HabitType.SCHEDULED,
                userId = r.userId,
                name = r.name,
                offsetMinutes = r.offsetMinutes,
                userDate = r.missedDate,
                langCode = r.langCode,
            )
        }
    }
}
