import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

object HabitService {

    enum class Type(val value: String) {
        SCHEDULED("scheduled"),
        COUNTER("counter");

        companion object {
            fun parse(s: String?): Type = entries.firstOrNull { it.value == s } ?: SCHEDULED
        }
    }

    enum class Direction(val value: String) {
        MORE("more"),
        LESS("less");

        companion object {
            fun parse(s: String?): Direction? = entries.firstOrNull { it.value == s }
        }
    }

    data class Habit(
        val id: Long,
        val userId: Long,
        val name: String,
        val type: Type,
        val dailyTarget: Int?,
        val direction: Direction?,
        val reminders: List<LocalTime>,
        val pausedAt: Instant?
    )

    fun addHabit(
        userId: Long,
        name: String,
        type: Type,
        reminders: List<LocalTime>,
        dailyTarget: Int? = null,
        direction: Direction? = null
    ): Habit {
        return using(sessionOf(DatabaseService.dataSource, returnGeneratedKey = true)) { session ->
            session.transaction { tx ->
                val habitId = tx.updateAndReturnGeneratedKey(
                    queryOf(
                        """
                        INSERT INTO habits (user_id, name, habit_type, daily_target, direction)
                        VALUES (?, ?, ?, ?, ?)
                        """.trimIndent(),
                        userId,
                        name,
                        type.value,
                        dailyTarget,
                        direction?.value
                    )
                ) ?: error("Failed to insert habit")

                reminders.forEach { time ->
                    tx.execute(
                        queryOf(
                            "INSERT INTO habit_reminders (habit_id, reminder_time) VALUES (?, ?)",
                            habitId,
                            time
                        )
                    )
                }

                Habit(
                    id = habitId,
                    userId = userId,
                    name = name,
                    type = type,
                    dailyTarget = dailyTarget,
                    direction = direction,
                    reminders = reminders.sorted(),
                    pausedAt = null
                )
            }
        }
    }

    fun listActive(userId: Long): List<Habit> {
        return sessionOf(DatabaseService.dataSource).use { session ->
            session.run(
                queryOf(
                """
                SELECT h.id, h.user_id, h.name, h.habit_type, h.daily_target, h.direction, h.paused_at,
                       COALESCE(
                           ARRAY_AGG(r.reminder_time ORDER BY r.reminder_time)
                               FILTER (WHERE r.reminder_time IS NOT NULL),
                           '{}'
                       ) AS times
                FROM habits h
                LEFT JOIN habit_reminders r ON r.habit_id = h.id
                WHERE h.user_id = ? AND h.deleted_at IS NULL
                GROUP BY h.id
                ORDER BY h.created_at
                """.trimIndent(),
                userId
            ).map { row ->
                @Suppress("UNCHECKED_CAST")
                val arr = row.underlying.getArray("times").array as Array<java.sql.Time>
                Habit(
                    id = row.long("id"),
                    userId = row.long("user_id"),
                    name = row.string("name"),
                    type = Type.parse(row.stringOrNull("habit_type")),
                    dailyTarget = row.intOrNull("daily_target"),
                    direction = Direction.parse(row.stringOrNull("direction")),
                    reminders = arr.map { it.toLocalTime() },
                    pausedAt = row.instantOrNull("paused_at")
                )
            }.asList
            )
        }
    }

    fun softDelete(habitId: Long, userId: Long): Boolean {
        return sessionOf(DatabaseService.dataSource).use { session ->
            session.update(
                queryOf(
                    """
                    UPDATE habits
                    SET deleted_at = now()
                    WHERE id = ? AND user_id = ? AND deleted_at IS NULL
                    """.trimIndent(),
                    habitId,
                    userId
                )
            ) > 0
        }
    }

    fun pause(habitId: Long, userId: Long): Boolean {
        return sessionOf(DatabaseService.dataSource).use { session ->
            session.update(
                queryOf(
                    """
                    UPDATE habits
                    SET paused_at = now()
                    WHERE id = ? AND user_id = ? AND deleted_at IS NULL AND paused_at IS NULL
                    """.trimIndent(),
                    habitId,
                    userId
                )
            ) > 0
        }
    }

    fun resume(habitId: Long, userId: Long): Boolean {
        return sessionOf(DatabaseService.dataSource).use { session ->
            session.update(
                queryOf(
                    """
                    UPDATE habits
                    SET paused_at = NULL
                    WHERE id = ? AND user_id = ? AND deleted_at IS NULL AND paused_at IS NOT NULL
                    """.trimIndent(),
                    habitId,
                    userId
                )
            ) > 0
        }
    }

    data class DueReminder(
        val reminderId: Long,
        val habitId: Long,
        val habitType: Type,
        val userId: Long,
        val name: String,
        val reminderTime: LocalTime,
        val userDate: LocalDate,
        val lang: Lang
    )

    fun findDue(): List<DueReminder> {
        val now = Instant.now()
        val rows = sessionOf(DatabaseService.dataSource).use { session ->
            session.run(
                queryOf(
                """
                SELECT r.id AS reminder_id, h.id AS habit_id, h.habit_type,
                       h.user_id, h.name, r.reminder_time,
                       us.timezone AS tz, us.language AS lang
                FROM habit_reminders r
                JOIN habits h ON h.id = r.habit_id
                JOIN user_settings us ON us.user_id = h.user_id
                LEFT JOIN checkins c
                    ON c.reminder_id = r.id
                   AND c.check_date = (now() AT TIME ZONE us.timezone)::date
                WHERE h.deleted_at IS NULL
                  AND h.paused_at IS NULL
                  AND us.timezone IS NOT NULL
                  AND (h.habit_type <> 'scheduled' OR c.id IS NULL)
                """.trimIndent()
            ).map { row ->
                RawDue(
                    reminderId = row.long("reminder_id"),
                    habitId = row.long("habit_id"),
                    habitType = Type.parse(row.stringOrNull("habit_type")),
                    userId = row.long("user_id"),
                    name = row.string("name"),
                    reminderTime = row.localTime("reminder_time"),
                    tzId = row.string("tz"),
                    langCode = row.stringOrNull("lang")
                )
            }.asList
            )
        }

        return rows.mapNotNull { r ->
            val tz = runCatching { ZoneId.of(r.tzId) }.getOrNull() ?: return@mapNotNull null
            val zdt = now.atZone(tz)
            val localMinute = zdt.toLocalTime().withSecond(0).withNano(0)
            if (localMinute != r.reminderTime) return@mapNotNull null
            val lang = r.langCode?.let { runCatching { Lang.valueOf(it) }.getOrNull() } ?: Lang.EN
            DueReminder(
                reminderId = r.reminderId,
                habitId = r.habitId,
                habitType = r.habitType,
                userId = r.userId,
                name = r.name,
                reminderTime = r.reminderTime,
                userDate = zdt.toLocalDate(),
                lang = lang
            )
        }
    }

    fun findById(habitId: Long, userId: Long): Habit? =
        listActive(userId).firstOrNull { it.id == habitId }

    private data class RawDue(
        val reminderId: Long,
        val habitId: Long,
        val habitType: Type,
        val userId: Long,
        val name: String,
        val reminderTime: LocalTime,
        val tzId: String,
        val langCode: String?
    )
}
