package me.proton.jobforandroid.fitliroutegps

import me.proton.jobforandroid.fitliroutegps.location.GpsIntervalPolicy
import org.junit.Assert.assertEquals
import org.junit.Test

class GpsIntervalPolicyTest {
    @Test fun threeSeconds() {
        assertEquals(3_000L, GpsIntervalPolicy.fromPreference("3000"))
    }

    @Test fun fiveSeconds() {
        assertEquals(5_000L, GpsIntervalPolicy.fromPreference("5000"))
    }

    @Test fun tenSeconds() {
        assertEquals(10_000L, GpsIntervalPolicy.fromPreference("10000"))
    }

    @Test fun missingOrInvalidValueFallsBackToFiveSeconds() {
        listOf(null, "", "invalid", "7000").forEach {
            assertEquals(5_000L, GpsIntervalPolicy.fromPreference(it))
        }
    }
}
