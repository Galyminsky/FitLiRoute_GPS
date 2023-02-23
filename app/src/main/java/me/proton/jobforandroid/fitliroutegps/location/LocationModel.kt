package me.proton.jobforandroid.fitliroutegps.location

import org.osmdroid.util.GeoPoint

data class LocationModel(
    val velocity: Float = 0.0f,
    val distance: Float = 0.0f,
    val geoPoints: ArrayList<GeoPoint>
)
