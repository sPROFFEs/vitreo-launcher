package dev.vitreo.launcher.core

import org.junit.Assert.*
import org.junit.Test

class HomeArrivalTest {
    @Test fun iconsStartBeyondTheirScreenEdgeAndFinishInTheirOriginalCells() {
        for (x in listOf(80f, 240f, 560f, 720f)) for (y in listOf(200f, 800f, 1500f)) {
            val start = homeArrivalOffset(x, y, 800f, 1600f, 64f, 0f)
            assertTrue(x + start.x < 0f || x + start.x > 800f || y + start.y < 0f || y + start.y > 1600f)
            val middle = homeArrivalOffset(x, y, 800f, 1600f, 64f, .5f)
            assertEquals(start.x / 2f, middle.x, .01f)
            assertEquals(start.y / 2f, middle.y, .01f)
            assertEquals(HomeArrivalOffset(0f, 0f), homeArrivalOffset(x, y, 800f, 1600f, 64f, 1f))
        }
    }
    @Test fun invalidGeometryAndProgressCannotProduceInfiniteTransforms() {
        assertEquals(HomeArrivalOffset(0f, 0f), homeArrivalOffset(10f, 10f, 0f, 0f, 64f, 0f))
        assertEquals(HomeArrivalOffset(0f, 0f), homeArrivalOffset(10f, 10f, 800f, 1600f, 64f, Float.NaN))
        val center = homeArrivalOffset(400f, 800f, 800f, 1600f, 64f, 0f)
        assertTrue(center.x.isFinite() && center.y > 800f)
    }
}
