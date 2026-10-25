package dev.vitreo.launcher.core

import dev.vitreo.launcher.island.IslandPreferences
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class IslandLayoutTest {
    @Test fun aoedeControlsRoundTripAndCannotActivateAnOverlay() {
        val a=IslandLayout(320,72,240,100,128,32,310,-100,320,160,320,100,1,true,380,15)
        assertEquals(a,IslandPreferences.decode(IslandPreferences.encode(a)))
        assertFalse(IslandPreferences.encode(a).has("enabled"))
        assertEquals(IslandLayout(),IslandPreferences.decode(JSONObject()))
    }
    @Test fun hostileLayoutIsClampedBeforePersistingOrDrawing() {
        val a=IslandLayout(width=Int.MAX_VALUE,height=-99,top=-100,horizontal=Int.MIN_VALUE,handleHeight=99,
            handleTop=-500,touchWidth=-1,touchHeight=999,touchTop=Int.MAX_VALUE,reservedBottom=-500,duration=-1).sanitized()
        assertEquals(320,a.width);assertEquals(28,a.height);assertEquals(0,a.top);assertEquals(-100,a.horizontal)
        assertEquals(32,a.handleHeight);assertEquals(-1,a.handleTop);assertEquals(48,a.touchWidth);assertEquals(160,a.touchHeight)
        assertEquals(320,a.touchTop);assertEquals(0,a.reservedBottom);assertEquals(3,a.duration)
    }
    @Test fun cameraCapsuleAndAccessibleTouchAreaUseSeparatePositions() {
        val b=checkNotNull(islandBounds(IslandLayout(),412,892,0,44,0,24))
        assertEquals(8,b.capsule.top);assertEquals(48,b.touch.top);assertEquals(60,b.handle!!.top)
        assertEquals(156,b.capsule.width);assertEquals(48,b.capsule.height)
    }
    @Test fun everyPositionFitsNarrowAndLandscapeDisplaysAndInsets() {
        for(width in listOf(180,320,412,800)) for(height in listOf(180,412,900)) for(x in listOf(-100,0,100)) {
            val a=IslandLayout(width=320,height=72,top=240,horizontal=x,touchHeight=160,touchTop=320,touchHorizontal=-x,handleTop=320)
            val b=checkNotNull(islandBounds(a,width,height,12,48,16,24))
            for(r in listOfNotNull(b.capsule,b.handle,b.touch,b.window)) {
                assertTrue(r.left>=20);assertTrue(r.right<=width-24);assertTrue(r.top>=0);assertTrue(r.bottom<=height-32)
                assertTrue(r.width>0 && r.height>0)
            }
        }
    }
    @Test fun belowPolicyMovesCapsuleTouchAndHandleTogetherOrYieldsIfNoSpace() {
        val a=IslandLayout(reservedBottom=130)
        val original=checkNotNull(islandBounds(a,412,892,0,44,0,24))
        val b=checkNotNull(islandBounds(a,412,892,0,44,0,24,true))
        assertEquals(138,b.window.top)
        assertEquals(b.capsule.top-original.capsule.top,b.touch.top-original.touch.top)
        assertNull(islandBounds(a.copy(reservedBottom=400),200,220,0,44,0,24,true))
    }
    @Test fun pauseAndManualReservationRespectExplicitCoexistenceChoice() {
        assertNull(islandBounds(IslandLayout(coexistence=1),412,892,0,44,0,24,true))
        assertNull(islandBounds(IslandLayout(coexistence=1,reserveAlways=true),412,892,0,44,0,24))
        val independent=checkNotNull(islandBounds(IslandLayout(coexistence=2,reserveAlways=true),412,892,0,44,0,24,true))
        assertEquals(8,independent.capsule.top)
        assertNull(checkNotNull(islandBounds(IslandLayout(handleHeight=0),412,892,0,44,0,24)).handle)
    }
    @Test fun eventUpdatesAreCoalescedAndReconnectionsDoNotReplayOldAlerts() {
        val e=IslandEvents();val now=100_000L
        assertFalse(e.accept("old","body",now-20_000,now))
        assertFalse(e.accept("future","body",now+20_000,now))
        assertTrue(e.accept("one","body",now,now))
        assertFalse(e.accept("one","body",now+1_000,now+1_000))
        assertTrue(e.accept("one","updated body",now+2_000,now+2_000))
        assertTrue(e.accept("two","body",now,now))
        assertTrue(e.accept("one","updated body",now+40_000,now+40_000))
    }
}
