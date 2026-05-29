package dev.anilbeesetti.nextplayer.feature.videopicker.screens.playlist

import androidx.compose.runtime.Stable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.domain.GetSortedMediaUseCase
import dev.anilbeesetti.nextplayer.core.domain.playlist.AddMediumToPlaylistUseCase
import dev.anilbeesetti.nextplayer.core.domain.playlist.GetPlaylistWithMediaUseCase
import dev.anilbeesetti.nextplayer.core.domain.playlist.RemoveMediumFromPlaylistUseCase
import dev.anilbeesetti.nextplayer.core.domain.playlist.ReorderPlaylistUseCase
import dev.anilbeesetti.nextplayer.core.domain.playlist.UpdatePlaylistLastPlayedUseCase
import dev.anilbeesetti.nextplayer.core.model.ApplicationPreferences
import dev.anilbeesetti.nextplayer.core.model.Playlist
import dev.anilbeesetti.nextplayer.core.model.Video
import dev.anilbeesetti.nextplayer.core.ui.base.DataState
import dev.anilbeesetti.nextplayer.feature.playlist.navigation.PlaylistArgs
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class PlaylistDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getPlaylistWithMediaUseCase: GetPlaylistWithMediaUseCase,
    private val getSortedMediaUseCase: GetSortedMediaUseCase,
    private val addMediumToPlaylistUseCase: AddMediumToPlaylistUseCase,
    private val removeMediumFromPlaylistUseCase: RemoveMediumFromPlaylistUseCase,
    private val reorderPlaylistUseCase: ReorderPlaylistUseCase,
    private val updatePlaylistLastPlayedUseCase: UpdatePlaylistLastPlayedUseCase,
    private val preferencesRepository: PreferencesRepository,
) : ViewModel() {

    private val playlistArgs = PlaylistArgs(savedStateHandle)
    val playlistId = playlistArgs.playlistId

    private val uiStateInternal = MutableStateFlow(PlaylistDetailUiState())
    val uiState = uiStateInternal.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                getPlaylistWithMediaUseCase(playlistId),
                getSortedMediaUseCase.invoke(folderPath = null),
                preferencesRepository.applicationPreferences
            ) { playlist, folder, prefs ->
                val allVideos = folder?.allMediaList ?: emptyList()
                val videoMap = allVideos.associateBy { it.uriString }
                val orderedVideos = playlist?.mediaUris?.mapNotNull { videoMap[it] } ?: emptyList()
                Triple(playlist, orderedVideos, prefs)
            }.collect { (playlist, videos, prefs) ->
                uiStateInternal.update {
                    it.copy(
                        dataState = DataState.Success(playlist),
                        videos = videos,
                        preferences = prefs
                    )
                }
            }
        }
    }

    fun onEvent(event: PlaylistDetailUiEvent) {
        when (event) {
            is PlaylistDetailUiEvent.AddMedia -> addMedia(event.mediumUris)
            is PlaylistDetailUiEvent.RemoveMedium -> removeMedium(event.mediumUri)
            is PlaylistDetailUiEvent.ReorderMedia -> reorderMedia(event.orderedUris)
            is PlaylistDetailUiEvent.UpdateSortOption -> {
                uiStateInternal.update { it.copy(sortOption = event.option) }
            }
        }
    }

    private fun addMedia(mediumUris: List<String>) {
        viewModelScope.launch {
            addMediumToPlaylistUseCase(playlistId, mediumUris)
        }
    }

    private fun removeMedium(mediumUri: String) {
        viewModelScope.launch {
            removeMediumFromPlaylistUseCase(playlistId, mediumUri)
        }
    }

    private fun reorderMedia(orderedUris: List<String>) {
        viewModelScope.launch {
            reorderPlaylistUseCase(playlistId, orderedUris)
        }
    }

    fun getRecentVideoIndex(): Int {
        val currentState = uiStateInternal.value
        val playlist = (currentState.dataState as? DataState.Success)?.value ?: return 0

        val currentList = currentState.sortedVideos
        if (currentList.isEmpty()) return 0

        val index = currentList.indexOfFirst { it.uriString == playlist.lastPlayedUri }
        return if (index != -1) index else 0
    }

    fun saveLastPlayed(uri: String) {
        viewModelScope.launch {
            updatePlaylistLastPlayedUseCase(playlistId, uri)
        }
    }
}

enum class PlaylistSortOption {
    ADDED_ASC,
    ADDED_DESC,
    NAME_ASC,
    NAME_DESC
}

@Stable
data class PlaylistDetailUiState(
    val dataState: DataState<Playlist?> = DataState.Loading,
    val videos: List<Video> = emptyList(),
    val sortOption: PlaylistSortOption = PlaylistSortOption.ADDED_ASC,
    val preferences: ApplicationPreferences = ApplicationPreferences()
) {
    val sortedVideos: List<Video>
        get() = when (sortOption) {
            PlaylistSortOption.ADDED_ASC -> videos
            PlaylistSortOption.ADDED_DESC -> videos.reversed()
            PlaylistSortOption.NAME_ASC -> videos.sortedBy { it.displayName.lowercase() }
            PlaylistSortOption.NAME_DESC -> videos.sortedByDescending { it.displayName.lowercase() }
        }
}

sealed interface PlaylistDetailUiEvent {
    data class AddMedia(val mediumUris: List<String>) : PlaylistDetailUiEvent
    data class RemoveMedium(val mediumUri: String) : PlaylistDetailUiEvent
    data class ReorderMedia(val orderedUris: List<String>) : PlaylistDetailUiEvent
    data class UpdateSortOption(val option: PlaylistSortOption) : PlaylistDetailUiEvent
}
