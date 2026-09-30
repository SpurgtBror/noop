package com.noop.analytics

import kotlin.math.roundToLong

data class TimedWorkoutPoint(
    val latE6: Int,
    val lonE6: Int,
    val tMs: Long,
    val activeElapsedMs: Long? = null,
    val segment: Int = 0,
)

/** One measured distance interval, including a separately labelled final partial interval. */
data class WorkoutSplit(
    val number: Int,
    val distanceM: Double,
    val durationMs: Long,
    val cumulativeMs: Long,
    val partial: Boolean,
)

/** Pure route-time fold shared in behavior with StrandAnalytics.WorkoutSplits. */
object WorkoutSplits {
    const val KILOMETER_M = 1000.0
    const val MILE_M = 1609.344

    fun calculate(points: List<TimedWorkoutPoint>, unitMeters: Double): List<WorkoutSplit> {
        if (points.size < 2 || !unitMeters.isFinite() || unitMeters <= 0) return emptyList()
        val first = points.first()
        val startTime = first.tMs
        if (startTime <= 0) return emptyList()
        fun elapsed(p: TimedWorkoutPoint): Long = p.activeElapsedMs ?: (p.tMs - startTime)
        var previous = first
        var previousTime = elapsed(first)
        var totalDistance = 0.0
        var nextBoundary = unitMeters
        var lastBoundaryTime = 0L
        val result = ArrayList<WorkoutSplit>()
        for (current in points.drop(1)) {
            val currentTime = elapsed(current)
            if (current.tMs <= previous.tMs || currentTime < previousTime ||
                current.segment < previous.segment ||
                !valid(previous) || !valid(current)) return emptyList()
            if (current.segment == previous.segment) {
                val segmentDistance = RouteMath.haversineMeters(
                    RouteMath.LatLng(previous.latE6 / 1_000_000.0, previous.lonE6 / 1_000_000.0),
                    RouteMath.LatLng(current.latE6 / 1_000_000.0, current.lonE6 / 1_000_000.0),
                )
                if (!segmentDistance.isFinite()) return emptyList()
                val endDistance = totalDistance + segmentDistance
                while (segmentDistance > 0 && nextBoundary <= endDistance) {
                    val fraction = (nextBoundary - totalDistance) / segmentDistance
                    val crossingMs = previousTime + ((currentTime - previousTime) * fraction).roundToLong()
                    result.add(WorkoutSplit(result.size + 1, unitMeters,
                        (crossingMs - lastBoundaryTime).coerceAtLeast(0), crossingMs, false))
                    lastBoundaryTime = crossingMs
                    nextBoundary += unitMeters
                }
                totalDistance = endDistance
            }
            previous = current
            previousTime = currentTime
        }
        val remainder = totalDistance - result.size * unitMeters
        if (remainder >= 5.0 && previousTime > lastBoundaryTime) {
            result.add(WorkoutSplit(result.size + 1, remainder,
                previousTime - lastBoundaryTime, previousTime, true))
        }
        return result
    }

    private fun valid(p: TimedWorkoutPoint): Boolean =
        p.latE6 in -90_000_000..90_000_000 && p.lonE6 in -180_000_000..180_000_000 && p.tMs > 0
}
