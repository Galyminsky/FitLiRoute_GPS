package me.proton.jobforandroid.fitliroutegps

import me.proton.jobforandroid.fitliroutegps.utils.PreferenceValueReader
import org.junit.Assert.assertEquals
import org.junit.Test

class PreferenceValueReaderTest {
    private val times = arrayOf("3000", "5000", "10000")
    private val colors = arrayOf("#FFC107", "#FA2C2C", "#4CAF50")

    @Test fun validSavedValuesAreRetained() {
        assertEquals("10000", PreferenceValueReader.read(times, "3000") { "10000" })
        assertEquals("#4CAF50", PreferenceValueReader.read(colors, "#FA2C2C") { "#4CAF50" })
    }

    @Test fun missingValuesUseExistingDefaults() {
        assertEquals("3000", PreferenceValueReader.read(times, "3000") { null })
        assertEquals("#FA2C2C", PreferenceValueReader.read(colors, "#FA2C2C") { null })
    }

    @Test fun unknownValuesUseExistingDefaults() {
        assertEquals("3000", PreferenceValueReader.read(times, "3000") { "7000" })
        assertEquals("#FA2C2C", PreferenceValueReader.read(colors, "#FA2C2C") { "bad-color" })
    }

    @Test fun wrongTypeFallsBackAfterClassCastException() {
        assertEquals("3000", PreferenceValueReader.read(times, "3000") {
            throw ClassCastException("Stored interval is not a String")
        })
        assertEquals("#FA2C2C", PreferenceValueReader.read(colors, "#FA2C2C") {
            throw ClassCastException("Stored color is not a String")
        })
    }
}
