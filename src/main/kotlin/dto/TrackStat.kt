package dto

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable

@Serializable
data class TrackStat(
    val trackId: Long,
    val name: String,
    val streak: Int,
    val loggedDays: Int,
    val totalDays: Int,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val trend: QuantityTrend? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val groupFields: List<TrackStat> = emptyList(),
    // Log-only tracks are pure journals: no streak/completion line, no verdict — just the recorded values.
    @EncodeDefault(EncodeDefault.Mode.NEVER) val logOnly: Boolean = false,
)

@Serializable
data class QuantityTrend(
    @EncodeDefault(EncodeDefault.Mode.NEVER) val unit: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val direction: Direction? = null,
    val today: Double,
    val recentAvg: Double,
    val overallAvg: Double,
    val windowDays: Int,
    // Timer tracks: values are seconds (render as durations) and the verdict is today-vs-target.
    @EncodeDefault(EncodeDefault.Mode.NEVER) val target: Double? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val isDuration: Boolean = false,
)
