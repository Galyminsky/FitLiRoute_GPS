package me.proton.jobforandroid.fitliroutegps.utils

object RoutePointsParser {
    fun parse(value: String): List<Pair<Double, Double>>? {
        if (value.isEmpty()) return emptyList()
        val parts = value.split('/')
        if (parts.size > 1_000_001) return null
        val points = ArrayList<Pair<Double, Double>>(parts.size)
        for ((index, part) in parts.withIndex()) {
            if (part.isEmpty() && index == parts.lastIndex) continue
            val coordinates = part.split(',')
            if (coordinates.size != 2) return null
            val latitude = coordinates[0].toDoubleOrNull() ?: return null
            val longitude = coordinates[1].toDoubleOrNull() ?: return null
            if (!latitude.isFinite() || !longitude.isFinite() ||
                latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
            points.add(latitude to longitude)
        }
        return points
    }
}
