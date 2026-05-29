package dev.anilbeesetti.nextplayer.feature.videopicker.navigation

import androidx.lifecycle.SavedStateHandle

internal const val PLAYLIST_ID_ARG = "playlistId"

internal class PlaylistArgs(savedStateHandle: SavedStateHandle) {
    val playlistId: Long = checkNotNull(savedStateHandle[PLAYLIST_ID_ARG])
}
