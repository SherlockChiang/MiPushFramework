package top.trumeet.mipushframework.main

import kotlin.math.abs

/**
 * Returns the destination adjacent to [currentRoute] for a horizontal page swipe.
 *
 * A negative drag distance is a left swipe (advance to the next page); a positive distance is a
 * right swipe (go back to the previous page). Keeping this calculation outside the composable
 * makes the gesture contract deterministic and easy to exercise without an Android device.
 */
internal fun routeAfterHorizontalSwipe(
    currentRoute: String?,
    dragDistancePx: Float,
    thresholdPx: Float,
    routes: List<String>,
): String? {
    if (currentRoute == null || routes.isEmpty()) return null
    if (!dragDistancePx.isFinite() || !thresholdPx.isFinite()) return null
    if (abs(dragDistancePx) < thresholdPx.coerceAtLeast(0f)) return null

    val currentIndex = routes.indexOf(currentRoute)
    if (currentIndex < 0) return null

    val targetIndex = if (dragDistancePx < 0f) {
        currentIndex + 1
    } else {
        currentIndex - 1
    }
    return routes.getOrNull(targetIndex)
}
