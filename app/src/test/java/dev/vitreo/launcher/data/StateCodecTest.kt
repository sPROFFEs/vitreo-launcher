package dev.vitreo.launcher.data

import dev.vitreo.launcher.core.*
import org.junit.Assert.*
import org.junit.Test

class StateCodecTest {
    private val a = "com.example.one/.Main"
    private val b = "com.example.two/.Main"
    private val c = "com.example.three/.Main"
    @Test fun roundTrip() {
        val state = LauncherState(initialized = true, home = listOf(HomeEntry.Folder("folder:1", "M\u00fasica", listOf(a, b))),
            dock = listOf(c), appearance = Appearance(palette = 2)).sanitized()
        assertEquals(state, StateCodec.decode(StateCodec.encode(state)))
    }
    @Test fun futureSchemaRejected() { assertThrows(IllegalArgumentException::class.java) { StateCodec.decode("{\"schema\":9}") } }
    @Test fun hugeInputRejected() { assertThrows(IllegalArgumentException::class.java) { StateCodec.decode("x".repeat(StateCodec.MAX_BYTES + 1)) } }
    @Test fun unknownElementRejected() { assertThrows(IllegalStateException::class.java) { StateCodec.decode("""{"schema":1,"home":[{"type":"intent","id":"bad"}],"dock":[]}""") } }
    @Test fun invalidAppRemoved() { assertTrue(StateCodec.decode("""{"schema":1,"home":[{"type":"app","id":"intent://bad"}],"dock":[]}""").home.isEmpty()) }
    @Test fun noSilentTruncationOfDock() { assertThrows(IllegalArgumentException::class.java) {
        StateCodec.decode("""{"schema":1,"home":[],"dock":["a/A","b/B","c/C","d/D","e/E"]}""")
    } }
    @Test fun duplicateConflictSanitized() {
        val state = LauncherState(initialized = true, home = listOf(HomeEntry.App(a)), dock = listOf(a, a))
        assertEquals(listOf(a), StateCodec.decode(StateCodec.encode(state)).placedApps())
    }
    @Test fun unicodeAndEscapesPreserved() {
        val s = LauncherState(initialized = true, home = listOf(HomeEntry.Folder("folder:test", "\u00d1 \"test\" \\ line", listOf(a,b)))).sanitized()
        assertEquals(s, StateCodec.decode(StateCodec.encode(s)))
    }
}
