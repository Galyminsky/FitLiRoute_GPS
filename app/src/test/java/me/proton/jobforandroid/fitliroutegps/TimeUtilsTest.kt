package me.proton.jobforandroid.fitliroutegps

import me.proton.jobforandroid.fitliroutegps.utils.TimeUtils
import org.junit.Assert.assertEquals
import org.junit.Test

class TimeUtilsTest {
    @Test fun displaysWholeSecondsWithoutMilliseconds() {
        assertEquals("00:00:00", TimeUtils.getTime(999L))
        assertEquals("01:02:03", TimeUtils.getTime(3_723_987L))
    }

    @Test fun elapsedHoursDoNotWrapAtMidnight() {
        assertEquals("25:00:01", TimeUtils.getTime(90_001_000L))
    }

    @Test fun negativeElapsedTimeIsClampedToZero() {
        assertEquals("00:00:00", TimeUtils.getTime(-1L))
    }
}
