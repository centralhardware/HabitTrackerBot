package services

import db.HabitRepository
import dto.Direction
import dto.DueReminder
import dto.Habit
import dto.HabitReminder
import dto.HabitStatus
import dto.HabitType
import java.time.Instant
import java.time.ZoneId

object HabitService {

    fun addHabit(
        userId: Long,
        name: String,
        type: HabitType,
        reminders: List<HabitReminder>,
        dailyTarget: Double? = null,
        unit: String? = null,
        direction: Direction? = null,
        logOnly: Boolean = false,
    ): Habit = HabitRepository.upsert(
        Habit(
            id = 0L,
            userId = userId,
            name = name,
            type = type,
            dailyTarget = dailyTarget,
            unit = unit,
            direction = direction,
            reminders = reminders.sortedBy { it.time },
            status = HabitStatus.ACTIVE,
            logOnly = logOnly
        )
    )

    /**
     * Создаёт мульти-полевую quantity-привычку: корень с именем группы и напоминаниями
     * + N полей со своими target/unit/direction. Чек-ины пишутся под id поля.
     */
    fun addHabitGroup(
        userId: Long,
        name: String,
        fields: List<FieldSpec>,
        reminders: List<HabitReminder>,
        logOnly: Boolean = false,
    ): Habit {
        require(fields.isNotEmpty()) { "Group must have at least one field" }
        val root = Habit(
            id = 0L,
            userId = userId,
            name = name,
            type = HabitType.QUANTITY,
            reminders = reminders.sortedBy { it.time },
            status = HabitStatus.ACTIVE,
            logOnly = logOnly
        )
        val fieldHabits = fields.map { f ->
            Habit(
                id = 0L,
                userId = userId,
                name = f.name,
                type = HabitType.QUANTITY,
                dailyTarget = f.dailyTarget,
                unit = f.unit,
                direction = f.direction,
                reminders = emptyList(),
                status = HabitStatus.ACTIVE,
                logOnly = logOnly
            )
        }
        return HabitRepository.insertGroup(root, fieldHabits)
    }

    data class FieldSpec(
        val name: String,
        val dailyTarget: Double? = null,
        val unit: String? = null,
        val direction: Direction? = null
    )

    fun listActive(userId: Long): List<Habit> = HabitRepository.listActive(userId)

    fun findById(habitId: Long, userId: Long): Habit? = HabitRepository.find(habitId, userId)

    fun findAnyRow(habitId: Long, userId: Long): Habit? = HabitRepository.findAnyRow(habitId, userId)

    fun listReminders(habitId: Long, userId: Long): List<HabitReminder> =
        HabitRepository.listReminders(habitId, userId)

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
        // Load-modify-save: flip status on the entity (root + group fields) and persist via the
        // generic row save. The cascade lives here in Kotlin, not in a specialized SQL statement.
        val rows = if (habit.isGroupRoot) listOf(habit) + habit.fields else listOf(habit)
        rows.forEach { HabitRepository.upsert(it.copy(status = to)) }
        return true
    }

    fun findDue(): List<DueReminder> {
        val now = Instant.now()
        return HabitRepository.findRawDue().mapNotNull { r ->
            val tz = runCatching { ZoneId.of(r.tzId) }.getOrNull() ?: return@mapNotNull null
            val zdt = now.atZone(tz)
            val localMinute = zdt.toLocalTime().withSecond(0).withNano(0)
            if (localMinute != r.reminderTime) return@mapNotNull null
            if (r.reminderDays.isNotEmpty() && zdt.dayOfWeek.value !in r.reminderDays) return@mapNotNull null
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
                langCode = r.langCode
            )
        }
    }
}
