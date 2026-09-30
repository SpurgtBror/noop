package com.noop.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutSplitsTest {
    private fun point(lonE6: Int, seconds: Long, activeSeconds: Long? = null, segment: Int = 0) =
        TimedWorkoutPoint(0, lonE6, 1_700_000_000_000L + seconds * 1000,
            activeSeconds?.times(1000), segment)

    @Test fun interpolatesMultipleBoundariesAndFinalPartial() {
        val route = listOf(point(0, 0), point(22_500, 600))
        val splits = WorkoutSplits.calculate(route, WorkoutSplits.KILOMETER_M)
        assertEquals(3, splits.size)
        assertEquals(listOf(false, false, true), splits.map { it.partial })
        assertTrue(kotlin.math.abs(splits[0].durationMs - 239_831) < 1_000)
        assertTrue(kotlin.math.abs(splits[1].durationMs - 239_831) < 1_000)
        assertTrue(splits[2].distanceM in 490.0..510.0)
        assertEquals(600_000L, splits.last().cumulativeMs)
        val miles = WorkoutSplits.calculate(route, WorkoutSplits.MILE_M)
        assertEquals(2, miles.size)
        assertEquals(WorkoutSplits.MILE_M, miles.first().distanceM, 0.001)
    }

    @Test fun pausedSegmentDoesNotAddDistanceAcrossGap() {
        val route = listOf(point(0, 0, 0), point(9_000, 300, 300),
            point(50_000, 600, 300, 1), point(59_000, 900, 600, 1))
        val splits = WorkoutSplits.calculate(route, 1000.0)
        assertEquals(2, splits.count { !it.partial })
        assertTrue(splits.last().cumulativeMs <= 600_000L)
    }

    @Test fun rejectsMissingOrBackwardsTiming() {
        assertTrue(WorkoutSplits.calculate(listOf(point(0, 0), point(9_000, 0)), 1000.0).isEmpty())
        assertTrue(WorkoutSplits.calculate(listOf(point(0, 1), point(9_000, 0)), 1000.0).isEmpty())
    }
}
