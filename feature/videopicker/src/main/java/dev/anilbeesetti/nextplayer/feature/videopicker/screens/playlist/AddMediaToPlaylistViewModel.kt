package dev.anilbeesetti.nextplayer.feature.videopicker.screens.playlist

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.anilbeesetti.nextplayer.core.domain.playlist.AddMediumToPlaylistUseCase
import dev.anilbeesetti.nextplayer.core.domain.playlist.GetPlaylistWithMediaUseCase
import dev.anilbeesetti.nextplayer.core.domain.GetSortedMediaUseCase
import dev.anilbeesetti.nextplayer.core.model.Video
import dev.anilbeesetti.nextplayer.core.ui.base.DataState
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class AddMediaToPlaylistViewModel @Inject constructor(
    private val getSortedMediaUseCase: GetSortedMediaUseCase,
    private val getPlaylistWithMediaUseCase: GetPlaylistWithMediaUseCase,
    private val addMediumToPlaylistUseCase: AddMediumToPlaylistUseCase,
) : ViewModel() {

    private var currentPlaylistId: Long = -1L

    private val uiStateInternal = MutableStateFlow(AddMediaToPlaylistUiState())
    val uiState = uiStateInternal.asStateFlow()

    fun initData(playlistId: Long) {
        if (currentPlaylistId == playlistId) return

        currentPlaylistId = playlistId

        viewModelScope.launch {
            combine(
                getSortedMediaUseCase.invoke(folderPath = null),
                getPlaylistWithMediaUseCase(currentPlaylistId),
            ) { folder, playlist ->
                val alreadyAddedUris = playlist?.mediaUris?.toSet() ?: emptySet()
                val allVideos = folder?.allMediaList ?: emptyList()
                Pair(allVideos, alreadyAddedUris)
            }.collect { (allVideos, alreadyAddedUris) ->
                uiStateInternal.update { currentState ->
                    currentState.copy(
                        dataState = DataState.Success(allVideos),
                        alreadyAddedUris = alreadyAddedUris,
                    )
                }
            }
        }
    }

    fun onEvent(event: AddMediaToPlaylistUiEvent) {
        when (event) {
            is AddMediaToPlaylistUiEvent.ToggleSelection -> toggleSelection(event.uri)
            is AddMediaToPlaylistUiEvent.Confirm -> confirmAddMedia()
            is AddMediaToPlaylistUiEvent.UpdateSearchQuery -> updateSearchQuery(event.query)
            is AddMediaToPlaylistUiEvent.ResetState -> {
                uiStateInternal.update {
                    it.copy(
                        isDone = false,
                        searchQuery = "",
                        selectedUris = emptySet()
                    )
                }
            }
        }
    }

    private fun toggleSelection(uri: String) {
        uiStateInternal.update { currentState ->
            val selected = currentState.selectedUris.toMutableSet()
            if (uri in selected) selected.remove(uri) else selected.add(uri)
            currentState.copy(selectedUris = selected)
        }
    }

    private fun confirmAddMedia() {
        if (currentPlaylistId == -1L) return

        viewModelScope.launch {
            val uris = uiStateInternal.value.selectedUris.toList()
            if (uris.isEmpty()) return@launch
            addMediumToPlaylistUseCase(currentPlaylistId, uris)
            uiStateInternal.update { it.copy(selectedUris = emptySet(), isDone = true) }
        }
    }

    private fun updateSearchQuery(query: String) {
        uiStateInternal.update { it.copy(searchQuery = query) }
    }
}

@Stable
data class AddMediaToPlaylistUiState(
    val dataState: DataState<List<Video>> = DataState.Loading,
    val alreadyAddedUris: Set<String> = emptySet(),
    val selectedUris: Set<String> = emptySet(),
    val searchQuery: String = "",
    val isDone: Boolean = false,
) {
    val filteredVideos: List<Video>
        get() = (dataState as? DataState.Success)?.value
            ?.filter { video ->
                video.uriString !in alreadyAddedUris &&
                        (searchQuery.isBlank() || video.displayName.contains(searchQuery, ignoreCase = true))
            } ?: emptyList()

    val selectedCount: Int get() = selectedUris.size
}

sealed interface AddMediaToPlaylistUiEvent {
    data class ToggleSelection(val uri: String) : AddMediaToPlaylistUiEvent
    data object Confirm : AddMediaToPlaylistUiEvent
    data class UpdateSearchQuery(val query: String) : AddMediaToPlaylistUiEvent
    data object ResetState : AddMediaToPlaylistUiEvent
}
