package me.proton.jobforandroid.fitliroutegps

import me.proton.jobforandroid.fitliroutegps.utils.SpeedUnitFormatter
import org.junit.Assert.assertEquals
import org.junit.Test

class SpeedUnitFormatterTest {
    @Test fun newValueKeepsNumberAndAddsLocalizedUnit() {
        assertEquals("12.3 km/h", SpeedUnitFormatter.kmh("12.3", "km/h"))
        assertEquals("12,3 км/ч", SpeedUnitFormatter.kmh("12,3", "км/ч"))
    }

    @Test fun legacyLabelIsCorrectedOnlyForDisplay() {
        assertEquals("12.3 km/h", SpeedUnitFormatter.kmh("12.3 m/s", "km/h"))
        assertEquals("12.3 км/ч", SpeedUnitFormatter.kmh("12.3 m/s", "км/ч"))
        assertEquals("12.3 км/ч", SpeedUnitFormatter.kmh("12.3 km/h", "км/ч"))
    }
}
