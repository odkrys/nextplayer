package dev.anilbeesetti.nextplayer.feature.videopicker.screens.playlist

import android.net.Uri
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.anilbeesetti.nextplayer.core.domain.playlist.AddMediumToPlaylistUseCase
import dev.anilbeesetti.nextplayer.core.domain.playlist.CreatePlaylistUseCase
import dev.anilbeesetti.nextplayer.core.domain.playlist.DeletePlaylistUseCase
import dev.anilbeesetti.nextplayer.core.domain.playlist.GetPlaylistsUseCase
import dev.anilbeesetti.nextplayer.core.domain.playlist.RenamePlaylistUseCase
import dev.anilbeesetti.nextplayer.core.domain.playlist.ReorderPlaylistsUseCase
import dev.anilbeesetti.nextplayer.core.model.Playlist
import dev.anilbeesetti.nextplayer.core.ui.base.DataState
import kotlinx.coroutines.channels.Channel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class PlaylistViewModel @Inject constructor(
    private val addMediumToPlaylistUseCase: AddMediumToPlaylistUseCase,
    private val getPlaylistsUseCase: GetPlaylistsUseCase,
    private val createPlaylistUseCase: CreatePlaylistUseCase,
    private val renamePlaylistUseCase: RenamePlaylistUseCase,
    private val deletePlaylistUseCase: DeletePlaylistUseCase,
    private val reorderPlaylistsUseCase: ReorderPlaylistsUseCase,
) : ViewModel() {

    private val _playlists = getPlaylistsUseCase()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null
        )

    val uiState: StateFlow<PlaylistUiState> = _playlists.map { playlists ->
        if (playlists == null) {
            PlaylistUiState(dataState = DataState.Loading)
        } else {
            PlaylistUiState(dataState = DataState.Success(playlists))
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PlaylistUiState()
    )

    private val _playlistEvent = Channel<String>()
    val playlistEvent = _playlistEvent.receiveAsFlow()

    fun onEvent(event: PlaylistUiEvent) {
        when (event) {
            is PlaylistUiEvent.CreatePlaylist -> createPlaylist(event.name)
            is PlaylistUiEvent.RenamePlaylist -> renamePlaylist(event.playlistId, event.name)
            is PlaylistUiEvent.DeletePlaylist -> deletePlaylist(event.playlistId)
            is PlaylistUiEvent.AddMediaToPlaylist -> addMediaToPlaylist(event.playlistId, event.uris, event.sizes)
            is PlaylistUiEvent.ReorderPlaylists -> reorderPlaylists(event.ids)
        }
    }

    private fun createPlaylist(name: String) {
        viewModelScope.launch {
            val currentPlaylists = _playlists.value ?: emptyList()
            val maxPosition = currentPlaylists.maxOfOrNull { it.position } ?: -1

            createPlaylistUseCase(name, maxPosition + 1).onFailure { throwable ->
                _playlistEvent.send(throwable.message ?: "Failed to create playlist")
            }
        }
    }

    private fun addMediaToPlaylist(playlistId: Long, uris: List<String>, sizes: List<Long>) {
        viewModelScope.launch {
            val targetPlaylist = (uiState.value.dataState as? DataState.Success)
                ?.value
                ?.find { it.id == playlistId }

            val currentUris = targetPlaylist?.media?.map { it.uri }?.toSet() ?: emptySet()
            val targetPlaylistName = targetPlaylist?.name ?: "playlist"

            val safeSizes = if (sizes.isEmpty()) List(uris.size) { 0L } else sizes
            val newItems = uris.zip(safeSizes).filter { (uri, _) ->
                val cleanUri = Uri.parse(uri).buildUpon().fragment(null).build().toString()
                cleanUri !in currentUris
            }

            val newUris = newItems.map { it.first }
            val newSizes = newItems.map { it.second }

            addMediumToPlaylistUseCase(playlistId, newUris, newSizes)

            val message = when {
                newUris.isEmpty() -> "All videos already in playlist '$targetPlaylistName'"
                newUris.size == uris.size -> "Added ${newUris.size} videos to the playlist '$targetPlaylistName'"
                else -> "Added ${newUris.size} videos to playlist '$targetPlaylistName' (${uris.size - newUris.size} already existed)"
            }
            _playlistEvent.send(message)
        }
    }

    private fun renamePlaylist(playlistId: Long, name: String) {
        viewModelScope.launch {
            renamePlaylistUseCase(playlistId, name).onFailure { throwable ->
                _playlistEvent.send(throwable.message ?: "Failed to rename playlist")
            }
        }
    }

    private fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch {
            deletePlaylistUseCase(playlistId)
        }
    }

    private fun reorderPlaylists(ids: List<Long>) {
        viewModelScope.launch {
            reorderPlaylistsUseCase(ids)
        }
    }
}

@Stable
data class PlaylistUiState(
    val dataState: DataState<List<Playlist>> = DataState.Loading,
)

sealed interface PlaylistUiEvent {
    data class CreatePlaylist(val name: String) : PlaylistUiEvent
    data class RenamePlaylist(val playlistId: Long, val name: String) : PlaylistUiEvent
    data class DeletePlaylist(val playlistId: Long) : PlaylistUiEvent
    data class AddMediaToPlaylist(val playlistId: Long, val uris: List<String>, val sizes: List<Long> = emptyList()) : PlaylistUiEvent
    data class ReorderPlaylists(val ids: List<Long>) : PlaylistUiEvent
}
