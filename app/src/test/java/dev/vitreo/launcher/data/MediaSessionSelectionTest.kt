package dev.vitreo.launcher.data

import android.media.session.PlaybackState
import org.junit.Assert.*
import org.junit.Test

class MediaSessionSelectionTest {
    private data class Player(val name: String, val state: Int?)
    private fun select(vararg players: Player) = selectMediaSession(players.toList()) { it.state }

    @Test fun pausedTrackWinsOverIdleAssistantEvenWhenAndroidRanksAssistantFirst() {
        val assistant = Player("Assistant", PlaybackState.STATE_NONE)
        val music = Player("Music", PlaybackState.STATE_PAUSED)
        assertEquals(music, select(assistant, music))
        assertNull(select(assistant))
    }

    @Test fun playbackTakesPriorityAndTiesKeepAndroidOrder() {
        val stopped = Player("Old player", PlaybackState.STATE_STOPPED)
        val paused = Player("Paused", PlaybackState.STATE_PAUSED)
        val buffering = Player("Loading", PlaybackState.STATE_BUFFERING)
        val playing = Player("Playing", PlaybackState.STATE_PLAYING)
        val second = Player("Another player", PlaybackState.STATE_PLAYING)
        assertEquals(playing, select(stopped, paused, buffering, playing, second))
        assertEquals(buffering, select(stopped, paused, buffering))
        assertEquals(paused, select(stopped, paused))
        assertEquals(stopped, select(stopped))
    }

    @Test fun emptyUnavailableAndErrorSessionsNeverBecomeAPlayer() {
        assertNull(select())
        assertNull(select(Player("Missing state", null), Player("Failed", PlaybackState.STATE_ERROR)))
    }
}
