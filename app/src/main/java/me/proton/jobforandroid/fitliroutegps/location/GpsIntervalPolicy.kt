package me.proton.jobforandroid.fitliroutegps.location

internal object GpsIntervalPolicy {
    fun fromPreference(value: String?): Long = when (value) {
        "3000" -> 3_000L
        "5000" -> 5_000L
        "10000" -> 10_000L
        else -> 5_000L
    }
}
