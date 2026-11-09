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

    @Test fun secondBoundaryUsesElapsedMillisecondsWithoutRoundingUp() {
        assertEquals("00:00:59", TimeUtils.getTime(59_999L))
        assertEquals("00:01:00", TimeUtils.getTime(60_000L))
        assertEquals("00:59:59", TimeUtils.getTime(3_599_999L))
        assertEquals("01:00:00", TimeUtils.getTime(3_600_000L))
    }

    @Test fun longElapsedDurationDoesNotWrapHours() {
        assertEquals("240:00:00", TimeUtils.getTime(864_000_000L))
    }
}
