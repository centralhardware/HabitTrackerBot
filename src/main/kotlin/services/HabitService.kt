package services

import db.HabitRepository
import dto.DueReminder
import dto.Habit
import dto.HabitStatus
import dto.HabitType
import java.time.Instant
import java.time.ZoneId

object HabitService {

    fun addHabit(habit: Habit): Habit = HabitRepository.upsert(
        habit.copy(
            id = 0L,
            status = HabitStatus.ACTIVE,
            reminders = habit.reminders.sortedBy { it.time },
        )
    )

    fun listActive(userId: Long): List<Habit> = HabitRepository.listActive(userId)

    fun findById(habitId: Long, userId: Long): Habit? = HabitRepository.find(habitId, userId)

    fun softDelete(habitId: Long, userId: Long): Boolean = transition(habitId, userId, HabitStatus.DELETED)

    fun pause(habitId: Long, userId: Long): Boolean =
        transition(habitId, userId, HabitStatus.PAUSED) { it.status == HabitStatus.ACTIVE }

    fun resume(habitId: Long, userId: Long): Boolean =
        transition(habitId, userId, HabitStatus.ACTIVE) { it.status == HabitStatus.PAUSED }

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
            if (localMinute != r.reminderTime) return@mapNotNull null
            val habitDow = if (r.nextDay) zdt.dayOfWeek.minus(1) else zdt.dayOfWeek
            val habitDate = if (r.nextDay) zdt.toLocalDate().minusDays(1) else zdt.toLocalDate()
            if (r.reminderDays.isNotEmpty() && habitDow.value !in r.reminderDays) return@mapNotNull null
            DueReminder(
                reminderId = r.reminderId,
                habitId = r.habitId,
                habitType = r.habitType,
                userId = r.userId,
                name = r.name,
                reminderTime = r.reminderTime,
                userDate = habitDate,
                langCode = r.langCode,
                nextDay = r.nextDay,
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
                reminderTime = r.reminderTime,
                userDate = r.missedDate,
                langCode = r.langCode,
                nextDay = r.nextDay,
            )
        }
    }
}
