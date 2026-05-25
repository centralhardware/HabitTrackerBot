import db.HabitRepository
import dto.Direction
import dto.DueReminder
import dto.Habit
import dto.HabitStatus
import dto.HabitType
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

object HabitService {

    fun addHabit(
        userId: Long,
        name: String,
        type: HabitType,
        reminders: List<LocalTime>,
        dailyTarget: Double? = null,
        unit: String? = null,
        direction: Direction? = null
    ): Habit = HabitRepository.upsert(
        Habit(
            id = 0L,
            userId = userId,
            name = name,
            type = type,
            dailyTarget = dailyTarget,
            unit = unit,
            direction = direction,
            reminders = reminders.sorted(),
            status = HabitStatus.ACTIVE
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
            DueReminder(
                reminderId = r.reminderId,
                habitId = r.habitId,
                habitType = r.habitType,
                userId = r.userId,
                name = r.name,
                reminderTime = r.reminderTime,
                userDate = zdt.toLocalDate(),
                langCode = r.langCode
            )
        }
    }
}
