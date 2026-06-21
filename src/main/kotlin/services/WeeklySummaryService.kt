package services

import db.CheckInRepository
import dto.CheckinValueRow
import dto.Direction
import dto.TrackStatus
import dto.TrackType
import dto.TrackWeekStat
import java.time.LocalDate

object WeeklySummaryService {

    fun weeklyStats(userId: Long, from: LocalDate, to: LocalDate): List<TrackWeekStat> {
        val tracks = TrackService.listActive(userId)
            .filter { it.status == TrackStatus.ACTIVE && !it.logOnly }
        if (tracks.isEmpty()) return emptyList()

        return tracks.flatMap { h ->
            val rows = CheckInRepository.loadForTrack(h.id)
            if (h.multiField) {
                h.params.map { p ->
                    weekStat(
                        name = "${h.name} / ${p.name}",
                        trackId = h.id,
                        type = h.type,
                        direction = p.direction,
                        dailyTarget = p.dailyTarget,
                        unit = p.unit,
                        hasSchedule = h.scheduled,
                        allowAdHoc = h.allowAdHoc,
                        rows = rows.filter { it.paramId == p.id },
                        from = from,
                        to = to,
                    )
                }
            } else {
                listOf(
                    weekStat(
                        name = h.name,
                        trackId = h.id,
                        type = h.type,
                        direction = h.direction,
                        dailyTarget = h.dailyTarget,
                        unit = h.unit,
                        hasSchedule = h.scheduled,
                        allowAdHoc = h.allowAdHoc,
                        rows = rows,
                        from = from,
                        to = to,
                    )
                )
            }
        }
    }

    private fun weekStat(
        name: String,
        trackId: Long,
        type: TrackType,
        direction: Direction?,
        dailyTarget: Double?,
        unit: String?,
        hasSchedule: Boolean,
        allowAdHoc: Boolean,
        rows: List<CheckinValueRow>,
        from: LocalDate,
        to: LocalDate,
    ): TrackWeekStat {
        val totals = CheckinAnalytics.weekTotals(rows, from, to)
        return TrackWeekStat(
            trackId = trackId,
            name = name,
            type = type,
            direction = direction,
            dailyTarget = dailyTarget,
            unit = unit,
            scheduledDone = totals.done,
            scheduledSkip = totals.skip,
            counterTotal = totals.total,
            counterDays = totals.days,
            quantityTotal = totals.quantityTotal,
            quantityDays = totals.quantityDays,
            targetHitDays = computeTargetHits(type, allowAdHoc, dailyTarget, direction, rows, from, to),
            hasSchedule = hasSchedule,
            allowAdHoc = allowAdHoc,
        )
    }

    private fun computeTargetHits(
        type: TrackType,
        allowAdHoc: Boolean,
        target: Double?,
        direction: Direction?,
        rows: List<CheckinValueRow>,
        from: LocalDate,
        to: LocalDate,
    ): Int {
        if (target == null) return 0
        return when (type) {
            // A check track's daily target applies to its ad-hoc counts; no target without ad-hoc.
            TrackType.CHECK -> {
                if (!allowAdHoc) return 0
                val targetInt = target.toInt()
                CheckinAnalytics.counterCountsPerDay(rows, from, to).values.count { count ->
                    if (direction == Direction.LESS) count <= targetInt
                    else count >= targetInt
                }
            }
            TrackType.QUANTITY, TrackType.TIMER -> {
                CheckinAnalytics.quantitySumsPerDayInRange(rows, from, to).values
                    .count { hits(it, target, direction) }
            }
        }
    }

    private fun hits(value: Double, target: Double, direction: Direction?): Boolean =
        if (direction == Direction.LESS) value <= target else value >= target
}
