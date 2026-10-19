package dev.vitreo.launcher.data

import android.media.session.PlaybackState

/** System priority can put an idle voice assistant before a paused music player. */
internal fun <T> selectMediaSession(sessions: List<T>, playbackState: (T) -> Int?): T? {
    fun priority(session: T): Int = when (playbackState(session)) {
        PlaybackState.STATE_PLAYING, PlaybackState.STATE_FAST_FORWARDING,
        PlaybackState.STATE_REWINDING, PlaybackState.STATE_SKIPPING_TO_NEXT,
        PlaybackState.STATE_SKIPPING_TO_PREVIOUS, PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM -> 4
        PlaybackState.STATE_BUFFERING, PlaybackState.STATE_CONNECTING -> 3
        PlaybackState.STATE_PAUSED -> 2
        PlaybackState.STATE_STOPPED -> 1
        else -> 0
    }
    // Preserve Android's ordering among equally active players; ignore empty/error sessions.
    return sessions.maxByOrNull(::priority)?.takeIf { priority(it) > 0 }
}
