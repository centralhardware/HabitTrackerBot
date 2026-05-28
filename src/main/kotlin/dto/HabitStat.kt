package dto

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable

@Serializable
data class HabitStat(
    val habitId: Long,
    val name: String,
    val streak: Int,
    val loggedDays: Int,
    val totalDays: Int,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val trend: QuantityTrend? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val groupFields: List<HabitStat> = emptyList(),
)

@Serializable
data class QuantityTrend(
    @EncodeDefault(EncodeDefault.Mode.NEVER) val unit: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val direction: Direction? = null,
    val today: Double,
    val recentAvg: Double,
    val overallAvg: Double,
    val windowDays: Int,
)
