package me.proton.jobforandroid.fitliroutegps.location

/** Geometry decision only; the service still calculates each segment with Location.distanceTo. */
object TrackFixPolicy {
    data class Decision(val addDistance: Boolean, val appendPoint: Boolean)

    fun decide(previousFix: DraftPoint?, lastRoutePoint: DraftPoint?, pointCount: Int,
               current: DraftPoint): Decision? {
        if (!valid(current)) return null
        val repeatedFirst = previousFix != null && pointCount == 1 && lastRoutePoint == current
        return Decision(addDistance = previousFix != null, appendPoint = !repeatedFirst)
    }

    fun restoredPoints(draft: TrackDraft): List<DraftPoint> =
        if (draft.points.isEmpty() && draft.lastPoint?.let(::valid) == true)
            listOf(draft.lastPoint) else draft.points

    private fun valid(point: DraftPoint): Boolean =
        point.latitude.isFinite() && point.longitude.isFinite() &&
            point.latitude in -90.0..90.0 && point.longitude in -180.0..180.0
}
