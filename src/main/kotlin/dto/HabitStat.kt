package dto

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

@OptIn(ExperimentalSerializationApi::class)
@JsonClassDiscriminator("kind")
@Serializable
sealed class HabitStat {
    abstract val habitId: Long
    abstract val name: String

    @Serializable
    @SerialName("scheduled")
    data class Scheduled(
        override val habitId: Long,
        override val name: String,
        val totalDays: Int,
        val doneCount: Int,
        val skipCount: Int,
        val streak: Int
    ) : HabitStat()

    @Serializable
    sealed class Counter : HabitStat() {
        abstract val todayCount: Int

        @Serializable
        @SerialName("counter.withTarget")
        data class WithTarget(
            override val habitId: Long,
            override val name: String,
            val dailyTarget: Int,
            @EncodeDefault(EncodeDefault.Mode.NEVER) val direction: Direction? = null,
            override val todayCount: Int,
            val doneDays: Int,
            val skipDays: Int,
            val streak: Int
        ) : Counter()

        @Serializable
        @SerialName("counter.trend")
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

        @Serializable
        @SerialName("counter.plain")
        data class Plain(
            override val habitId: Long,
            override val name: String,
            override val todayCount: Int,
            val grandTotal: Int,
            val daysLogged: Int
        ) : Counter()
    }

    @Serializable
    sealed class Quantity : HabitStat() {
        abstract val todayTotal: Double
        abstract val unit: String?

        @Serializable
        @SerialName("quantity.withTarget")
        data class WithTarget(
            override val habitId: Long,
            override val name: String,
            @EncodeDefault(EncodeDefault.Mode.NEVER) override val unit: String? = null,
            val dailyTarget: Double,
            @EncodeDefault(EncodeDefault.Mode.NEVER) val direction: Direction? = null,
            override val todayTotal: Double,
            val doneDays: Int,
            val skipDays: Int,
            val streak: Int
        ) : Quantity()

        @Serializable
        @SerialName("quantity.trend")
        data class Trend(
            override val habitId: Long,
            override val name: String,
            @EncodeDefault(EncodeDefault.Mode.NEVER) override val unit: String? = null,
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

        @Serializable
        @SerialName("quantity.plain")
        data class Plain(
            override val habitId: Long,
            override val name: String,
            @EncodeDefault(EncodeDefault.Mode.NEVER) override val unit: String? = null,
            override val todayTotal: Double,
            val grandTotal: Double,
            val daysLogged: Int
        ) : Quantity()
    }
}
