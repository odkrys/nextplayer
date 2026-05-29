package dev.anilbeesetti.nextplayer.feature.videopicker.screens.playlist

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.anilbeesetti.nextplayer.core.domain.playlist.AddMediumToPlaylistUseCase
import dev.anilbeesetti.nextplayer.core.domain.playlist.CreatePlaylistUseCase
import dev.anilbeesetti.nextplayer.core.domain.playlist.DeletePlaylistUseCase
import dev.anilbeesetti.nextplayer.core.domain.playlist.GetPlaylistsUseCase
import dev.anilbeesetti.nextplayer.core.domain.playlist.RenamePlaylistUseCase
import dev.anilbeesetti.nextplayer.core.model.Playlist
import dev.anilbeesetti.nextplayer.core.ui.base.DataState
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class PlaylistViewModel @Inject constructor(
    private val addMediumToPlaylistUseCase: AddMediumToPlaylistUseCase,
    private val getPlaylistsUseCase: GetPlaylistsUseCase,
    private val createPlaylistUseCase: CreatePlaylistUseCase,
    private val renamePlaylistUseCase: RenamePlaylistUseCase,
    private val deletePlaylistUseCase: DeletePlaylistUseCase,
) : ViewModel() {

    private val uiStateInternal = MutableStateFlow(PlaylistUiState())
    val uiState = uiStateInternal.asStateFlow()

    init {
        viewModelScope.launch {
            getPlaylistsUseCase().collect { playlists ->
                uiStateInternal.update { currentState ->
                    currentState.copy(
                        dataState = DataState.Success(playlists),
                    )
                }
            }
        }
    }

    fun onEvent(event: PlaylistUiEvent) {
        when (event) {
            is PlaylistUiEvent.CreatePlaylist -> createPlaylist(event.name)
            is PlaylistUiEvent.RenamePlaylist -> renamePlaylist(event.playlistId, event.name)
            is PlaylistUiEvent.DeletePlaylist -> deletePlaylist(event.playlistId)
            is PlaylistUiEvent.AddMediaToPlaylist -> addMediaToPlaylist(event.playlistId, event.uris)
        }
    }

    private fun createPlaylist(name: String) {
        viewModelScope.launch {
            createPlaylistUseCase(name).onFailure { throwable ->
                uiStateInternal.update { it.copy(errorMessage = throwable.message) }
            }
        }
    }

    private fun addMediaToPlaylist(playlistId: Long, uris: List<String>) {
        viewModelScope.launch {
            addMediumToPlaylistUseCase(playlistId, uris)
            uiStateInternal.update { it.copy(isDone = true) }
        }
    }

    private fun renamePlaylist(playlistId: Long, name: String) {
        viewModelScope.launch {
            renamePlaylistUseCase(playlistId, name).onFailure { throwable ->
                uiStateInternal.update { it.copy(errorMessage = throwable.message) }
            }
        }
    }

    private fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch {
            deletePlaylistUseCase(playlistId)
        }
    }
}

@Stable
data class PlaylistUiState(
    val dataState: DataState<List<Playlist>> = DataState.Loading,
    val errorMessage: String? = null,
    val isDone: Boolean = false,
)

sealed interface PlaylistUiEvent {
    data class CreatePlaylist(val name: String) : PlaylistUiEvent
    data class RenamePlaylist(val playlistId: Long, val name: String) : PlaylistUiEvent
    data class DeletePlaylist(val playlistId: Long) : PlaylistUiEvent
    data class AddMediaToPlaylist(val playlistId: Long, val uris: List<String>) : PlaylistUiEvent
}
