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

    @Test fun acceptsGeographicBoundsAndSinglePoint() {
        assertEquals(listOf(-90.0 to 180.0, 90.0 to -180.0),
            RoutePointsParser.parse("-90.0,180.0/90.0,-180.0/"))
        assertEquals(listOf(0.0 to 0.0), RoutePointsParser.parse("0.0,0.0"))
    }

    @Test fun rejectsMissingAndExtraCoordinateFields() {
        assertNull(RoutePointsParser.parse("52.1/"))
        assertNull(RoutePointsParser.parse("52.1,62.2,5.0/"))
        assertNull(RoutePointsParser.parse("/52.1,62.2/"))
    }

    @Test fun rejectsNonFiniteAndOutOfRangeLongitude() {
        assertNull(RoutePointsParser.parse("52.1,Infinity/"))
        assertNull(RoutePointsParser.parse("52.1,-180.0001/"))
    }
}
