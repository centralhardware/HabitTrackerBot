package services

import db.HabitRepository
import dto.Direction
import dto.DueReminder
import dto.Habit
import dto.HabitParam
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
            // Every habit carries >=1 param; a single-field habit keeps its metadata on the habit row.
            params = listOf(HabitParam(id = 0)),
            logOnly = logOnly
        )
    )

    /**
     * Создаёт мульти-полевую quantity-привычку: привычку с именем и напоминаниями плюс N params
     * со своими target/unit/direction. Чек-ины пишутся под id param-а.
     */
    fun addHabitGroup(
        userId: Long,
        name: String,
        params: List<ParamSpec>,
        reminders: List<HabitReminder>,
        logOnly: Boolean = false,
    ): Habit {
        require(params.isNotEmpty()) { "Quantity habit must have at least one param" }
        return HabitRepository.upsert(
            Habit(
                id = 0L,
                userId = userId,
                name = name,
                type = HabitType.QUANTITY,
                reminders = reminders.sortedBy { it.time },
                status = HabitStatus.ACTIVE,
                params = params.mapIndexed { i, s ->
                    HabitParam(
                        id = 0,
                        name = s.name,
                        unit = s.unit,
                        direction = s.direction,
                        dailyTarget = s.dailyTarget,
                        position = i,
                    )
                },
                logOnly = logOnly
            )
        )
    }

    data class ParamSpec(
        val name: String,
        val dailyTarget: Double? = null,
        val unit: String? = null,
        val direction: Direction? = null
    )

    fun listActive(userId: Long): List<Habit> = HabitRepository.listActive(userId)

    fun findById(habitId: Long, userId: Long): Habit? = HabitRepository.find(habitId, userId)

    fun findAnyRow(habitId: Long, userId: Long): Habit? = HabitRepository.findAnyRow(habitId, userId)

    fun firstParamId(habitId: Long, userId: Long): Long? = HabitRepository.firstParamId(habitId, userId)

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
