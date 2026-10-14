package dev.vitreo.launcher.core

import org.junit.Assert.*
import org.junit.Test

class WidgetConfigurationRequestsTest {
    @Test fun overlappingResultsReachTheirOriginalCallbacks() {
        val requests = WidgetConfigurationRequests()
        val results = mutableListOf<String>()
        val old = requests.register { results += "old:$it" }
        val current = requests.register { results += "current:$it" }
        assertTrue(requests.complete(old, false))
        assertEquals(listOf("old:false"), results)
        assertTrue(requests.complete(current, true))
        assertEquals(listOf("old:false", "current:true"), results)
    }

    @Test fun duplicateAndUnknownResultsAreIgnored() {
        val requests = WidgetConfigurationRequests()
        var calls = 0
        val code = requests.register { calls++ }
        assertFalse(requests.complete(123, true))
        assertTrue(requests.complete(code, true))
        assertFalse(requests.complete(code, true))
        assertEquals(1, calls)
        assertTrue(code in 0..0xffff)
    }

    @Test fun recreatedOwnerDoesNotConsumeAnOldResult() {
        val old = WidgetConfigurationRequests().register {}
        val recreated = WidgetConfigurationRequests()
        var completed = false
        val current = recreated.register { completed = it }
        assertNotEquals(old, current)
        assertFalse(recreated.complete(old, true))
        assertFalse(completed)
        assertTrue(recreated.complete(current, true))
        assertTrue(completed)
    }
}
