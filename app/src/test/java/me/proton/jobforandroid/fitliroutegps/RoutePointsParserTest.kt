package me.proton.jobforandroid.fitliroutegps

import me.proton.jobforandroid.fitliroutegps.utils.RoutePointsParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RoutePointsParserTest {
    @Test fun acceptsExistingRouteFormatWithTrailingSlash() {
        assertEquals(listOf(52.1 to 62.2, 52.3 to 62.4),
            RoutePointsParser.parse("52.1,62.2/52.3,62.4/"))
    }

    @Test fun acceptsEmptyRouteWithoutThrowing() {
        assertEquals(emptyList<Pair<Double, Double>>(), RoutePointsParser.parse(""))
    }

    @Test fun rejectsDamagedAndOutOfRangeRoutesWithoutPartialResult() {
        assertNull(RoutePointsParser.parse("52.1,62.2/broken/"))
        assertNull(RoutePointsParser.parse("52.1,62.2/95.0,10.0/"))
        assertNull(RoutePointsParser.parse("NaN,62.2/"))
        assertNull(RoutePointsParser.parse("52.1,62.2//"))
    }
}
