package me.proton.jobforandroid.fitliroutegps.utils

object SpeedUnitFormatter {
    fun kmh(value: String, unit: String): String {
        val number = listOf(" m/s", " km/h", " км/ч")
            .firstOrNull(value::endsWith)?.let(value::removeSuffix) ?: value
        return "$number $unit"
    }
}
