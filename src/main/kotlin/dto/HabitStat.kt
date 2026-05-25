package dto

sealed class HabitStat {
    abstract val habitId: Long
    abstract val name: String

    data class Scheduled(
        override val habitId: Long,
        override val name: String,
        val totalDays: Int,
        val doneCount: Int,
        val skipCount: Int,
        val streak: Int
    ) : HabitStat()

    sealed class Counter : HabitStat() {
        abstract val todayCount: Int

        data class WithTarget(
            override val habitId: Long,
            override val name: String,
            val dailyTarget: Int,
            val direction: Direction?,
            override val todayCount: Int,
            val doneDays: Int,
            val skipDays: Int,
            val streak: Int
        ) : Counter()

        data class Trend(
            override val habitId: Long,
            override val name: String,
            val direction: Direction,
            override val todayCount: Int,
            val yesterdayCount: Int,
            val grandTotal: Int,
            val daysLogged: Int,
            val overallAvg: Double,
            val recent3Avg: Double,
            val previous3Avg: Double,
            val recent7Avg: Double,
            val previous7Avg: Double
        ) : Counter()

        data class Plain(
            override val habitId: Long,
            override val name: String,
            override val todayCount: Int,
            val grandTotal: Int,
            val daysLogged: Int
        ) : Counter()
    }

    sealed class Quantity : HabitStat() {
        abstract val todayTotal: Double
        abstract val unit: String?

        data class WithTarget(
            override val habitId: Long,
            override val name: String,
            override val unit: String?,
            val dailyTarget: Double,
            val direction: Direction?,
            override val todayTotal: Double,
            val doneDays: Int,
            val skipDays: Int,
            val streak: Int
        ) : Quantity()

        data class Trend(
            override val habitId: Long,
            override val name: String,
            override val unit: String?,
            val direction: Direction,
            override val todayTotal: Double,
            val yesterdayTotal: Double,
            val grandTotal: Double,
            val daysLogged: Int,
            val overallAvg: Double,
            val recent3Avg: Double,
            val previous3Avg: Double,
            val recent7Avg: Double,
            val previous7Avg: Double
        ) : Quantity()

        data class Plain(
            override val habitId: Long,
            override val name: String,
            override val unit: String?,
            override val todayTotal: Double,
            val grandTotal: Double,
            val daysLogged: Int
        ) : Quantity()
    }
}
