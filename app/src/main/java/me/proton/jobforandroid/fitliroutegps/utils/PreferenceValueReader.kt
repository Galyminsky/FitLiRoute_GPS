package me.proton.jobforandroid.fitliroutegps.utils

object PreferenceValueReader {
    fun read(values: Array<String>, default: String, getValue: () -> String?): String {
        val saved = try { getValue() } catch (_: ClassCastException) { null }
        return saved?.takeIf { it in values } ?: default
    }
}
