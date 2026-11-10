package me.proton.jobforandroid.fitliroutegps

import me.proton.jobforandroid.fitliroutegps.location.DraftPoint
import me.proton.jobforandroid.fitliroutegps.location.TrackDraft
import me.proton.jobforandroid.fitliroutegps.location.TrackDraftCodec
import me.proton.jobforandroid.fitliroutegps.location.TrackFixPolicy
import org.junit.Assert.assertEquals
import org.junit.Test

class TrackFixPolicyTest {
    private val first = DraftPoint(52.0, 62.0)
    private val second = DraftPoint(52.1, 62.1)

    private class Route(previous: DraftPoint? = null, initial: List<DraftPoint> = emptyList(),
                        initialDistance: Float = 0f) {
        var previous = previous
        val points = initial.toMutableList()
        var distance = initialDistance

        fun fix(point: DraftPoint, segmentMeters: Float) {
            val decision = TrackFixPolicy.decide(previous, points.lastOrNull(), points.size, point)
                ?: return
            if (decision.addDistance) distance += segmentMeters
            if (decision.appendPoint) points.add(point)
            previous = point
        }
    }

    @Test fun zeroFixesRemainEmptyAndAtZeroDistance() {
        val route = Route()
        assertEquals(emptyList<DraftPoint>(), route.points)
        assertEquals(0f, route.distance)
    }

    @Test fun firstFixIsRecordedWithoutDistance() {
        val route = Route()
        route.fix(first, 999f)
        assertEquals(listOf(first), route.points)
        assertEquals(0f, route.distance)
        assertEquals(first, route.previous)
    }

    @Test fun twoFixesRetainOriginalSegmentDistance() {
        val route = Route()
        route.fix(first, 999f)
        route.fix(second, 125.5f)
        assertEquals(listOf(first, second), route.points)
        assertEquals(125.5f, route.distance)
    }

    @Test fun repeatedFirstCoordinateIsNotDuplicated() {
        val route = Route()
        route.fix(first, 999f)
        route.fix(first, 0f)
        route.fix(second, 125.5f)
        assertEquals(listOf(first, second), route.points)
        assertEquals(125.5f, route.distance)
    }

    @Test fun oldDraftRestoresPreviousFixAndCompatibleGeometry() {
        val old = TrackDraft(7, true, 1_000L, 0f, 0f, first, emptyList(), 0L, 2_000L)
        val decoded = TrackDraftCodec.decode(TrackDraftCodec.encode(old))
        val route = Route(decoded.lastPoint, TrackFixPolicy.restoredPoints(decoded), decoded.distance)
        route.fix(second, 125.5f)
        assertEquals(listOf(first, second), route.points)
        assertEquals(125.5f, route.distance)
        val existingRoute = old.copy(points = listOf(second), lastPoint = second, sequence = 1)
        assertEquals(existingRoute.points, TrackFixPolicy.restoredPoints(existingRoute))
    }

    @Test fun invalidFixDoesNotBecomeFirstPointOrDistanceAnchor() {
        val route = Route()
        route.fix(DraftPoint(Double.NaN, 62.0), 999f)
        route.fix(DraftPoint(91.0, 62.0), 999f)
        assertEquals(emptyList<DraftPoint>(), route.points)
        assertEquals(null, route.previous)
        route.fix(first, 999f)
        assertEquals(listOf(first), route.points)
        assertEquals(0f, route.distance)
    }
}
