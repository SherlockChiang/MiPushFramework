package top.trumeet.mipushframework.main

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MainPageSwipeNavigationTest {
    private val routes = listOf("records", "apps", "settings")

    @Test
    fun leftSwipeAdvancesAndRightSwipeReturns() {
        assertEquals(
            "settings",
            routeAfterHorizontalSwipe("apps", dragDistancePx = -100f, thresholdPx = 72f, routes),
        )
        assertEquals(
            "records",
            routeAfterHorizontalSwipe("apps", dragDistancePx = 100f, thresholdPx = 72f, routes),
        )
    }

    @Test
    fun shortSwipeAndEdgeSwipeDoNotNavigate() {
        assertNull(routeAfterHorizontalSwipe("apps", 71f, 72f, routes))
        assertNull(routeAfterHorizontalSwipe("records", 100f, 72f, routes))
        assertNull(routeAfterHorizontalSwipe("settings", -100f, 72f, routes))
    }

    @Test
    fun unknownOrInvalidInputIsIgnored() {
        assertNull(routeAfterHorizontalSwipe("missing", -100f, 72f, routes))
        assertNull(routeAfterHorizontalSwipe(null, -100f, 72f, routes))
        assertNull(routeAfterHorizontalSwipe("apps", Float.NaN, 72f, routes))
        assertNull(routeAfterHorizontalSwipe("apps", -100f, Float.POSITIVE_INFINITY, routes))
    }
}
