package dev.anilbeesetti.nextplayer.feature.videopicker.screens.search

import android.net.Uri
import androidx.compose.runtime.Stable
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.anilbeesetti.nextplayer.core.data.repository.MediaRepository
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.data.repository.SearchHistoryRepository
import dev.anilbeesetti.nextplayer.core.domain.GetPopularFoldersUseCase
import dev.anilbeesetti.nextplayer.core.domain.GetSortedVideosUseCase
import dev.anilbeesetti.nextplayer.core.domain.SearchMediaUseCase
import dev.anilbeesetti.nextplayer.core.domain.SearchResults
import dev.anilbeesetti.nextplayer.core.media.services.MediaOperationsService
import dev.anilbeesetti.nextplayer.core.model.ApplicationPreferences
import dev.anilbeesetti.nextplayer.core.model.Folder
import dev.anilbeesetti.nextplayer.core.model.MediaViewMode
import dev.anilbeesetti.nextplayer.core.model.Sort
import dev.anilbeesetti.nextplayer.core.model.Video
import dev.anilbeesetti.nextplayer.feature.videopicker.state.SelectionItem
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val getPopularFoldersUseCase: GetPopularFoldersUseCase,
    private val getSortedVideosUseCase: GetSortedVideosUseCase,
    private val mediaOperationsService: MediaOperationsService,
    private val mediaRepository: MediaRepository,
    private val searchMediaUseCase: SearchMediaUseCase,
    private val searchHistoryRepository: SearchHistoryRepository,
    private val preferencesRepository: PreferencesRepository,
) : ViewModel() {

    private val uiStateInternal = MutableStateFlow(SearchUiState())
    val uiState = uiStateInternal.asStateFlow()

    private val searchQuery = MutableStateFlow("")

    init {
        collectSearchHistory()
        collectPopularFolders()
        collectPreferences()
        collectSearchResults()
    }

    private fun collectSearchHistory() {
        viewModelScope.launch {
            searchHistoryRepository.searchHistory.collect { history ->
                uiStateInternal.update { it.copy(searchHistory = history) }
            }
        }
    }

    private fun collectPopularFolders() {
        viewModelScope.launch {
            getPopularFoldersUseCase(limit = 5).collect { folders ->
                uiStateInternal.update { it.copy(popularFolders = folders) }
            }
        }
    }

    private fun collectPreferences() {
        viewModelScope.launch {
            preferencesRepository.applicationPreferences.collect { prefs ->
                uiStateInternal.update { it.copy(preferences = prefs) }
            }
        }
    }

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private fun collectSearchResults() {
        viewModelScope.launch {
            searchQuery
                .debounce(SEARCH_DEBOUNCE_MS)
                .flatMapLatest { query ->
                    searchMediaUseCase(query)
                }
                .collect { results ->
                    val prefs = uiState.value.preferences
                    val forcedSortedVideos = when (prefs.sortBy) {
                        Sort.By.TITLE -> results.videos.sortedBy { it.nameWithExtension }
                        Sort.By.DATE -> results.videos.sortedBy { it.dateModified }
                        Sort.By.SIZE -> results.videos.sortedBy { it.size }
                        Sort.By.LENGTH -> results.videos.sortedBy { it.duration }
                        Sort.By.PATH -> results.videos.sortedBy { it.path }
                    }.let { list ->
                        if (prefs.sortOrder == Sort.Order.DESCENDING) list.reversed() else list
                    }
                    uiStateInternal.update {
                        it.copy(
                            //searchResults = results,
                            searchResults = results.copy(videos = forcedSortedVideos),
                            isSearching = false,
                        )
                    }
                }
        }
    }

    fun onEvent(event: SearchUiEvent) {
        when (event) {
            is SearchUiEvent.OnQueryChange -> onQueryChange(event.query)
            is SearchUiEvent.OnSearch -> onSearch(event.query)
            is SearchUiEvent.OnHistoryItemClick -> onHistoryItemClick(event.query)
            is SearchUiEvent.OnRemoveHistoryItem -> removeHistoryItem(event.query)
            is SearchUiEvent.OnClearHistory -> clearHistory()
            is SearchUiEvent.DeleteSelectedItems -> deleteSelectedItems(event.selectionItems)
            is SearchUiEvent.ShareSelectedItems -> shareSelectedItems(event.selectionItems)
            is SearchUiEvent.ShowMediaInfo -> showMediaInfo(event.video)
            is SearchUiEvent.RenameVideo -> renameVideo(event.uri, event.to)
            is SearchUiEvent.ClearPlaybackHistory -> clearPlaybackHistory(event.selectionItems)
            SearchUiEvent.DismissMediaInfo -> uiStateInternal.update { it.copy(mediaInfo = null) }
        }
    }

    private fun onQueryChange(query: String) {
        uiStateInternal.update { it.copy(query = query, isSearching = query.isNotBlank()) }
        searchQuery.value = query
    }

    private fun onSearch(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            searchHistoryRepository.addSearchQuery(query)
        }
    }

    private fun onHistoryItemClick(query: String) {
        uiStateInternal.update { it.copy(query = query, isSearching = true) }
        searchQuery.value = query
        onSearch(query)
    }

    private fun removeHistoryItem(query: String) {
        viewModelScope.launch {
            searchHistoryRepository.removeSearchQuery(query)
        }
    }

    private fun clearHistory() {
        viewModelScope.launch {
            searchHistoryRepository.clearHistory()
        }
    }

    private fun deleteSelectedItems(selectedItems: Set<SelectionItem>) {
        viewModelScope.launch {
            val videoUris = selectedItems.toVideoUris()
            mediaOperationsService.deleteMedia(videoUris)
        }
    }

    private fun shareSelectedItems(selectedItems: Set<SelectionItem>) {
        viewModelScope.launch {
            val videoUris = selectedItems.toVideoUris()
            mediaOperationsService.shareMedia(videoUris)
        }
    }

    private fun showMediaInfo(video: Video) {
        viewModelScope.launch {
            val mediaInfo = mediaRepository.getMediaInfo(video.uriString)
            if (mediaInfo != null) {
                uiStateInternal.update { it.copy(mediaInfo = mediaInfo) }
            }
        }
    }

    private fun renameVideo(uri: Uri, to: String) {
        viewModelScope.launch {
            mediaOperationsService.renameMedia(uri, to)
        }
    }

    private suspend fun Set<SelectionItem>.toVideoUris(): List<Uri> {
        val preferences = uiStateInternal.value.preferences
        return flatMap { selectionItem ->
            when (selectionItem) {
                is SelectionItem.Video -> listOf(selectionItem.uriString.toUri())
                is SelectionItem.Folder -> {
                    val videos = getSortedVideosUseCase(selectionItem.path).first()
                    // In FOLDERS mode, only include direct children
                    val filteredVideos = if (preferences.mediaViewMode == MediaViewMode.FOLDERS) {
                        videos.filter { it.parentPath == selectionItem.path }
                    } else {
                        videos
                    }
                    filteredVideos.map { it.uriString.toUri() }
                }
            }
        }
    }

    private fun clearPlaybackHistory(selectionItems: Set<SelectionItem>) {
        viewModelScope.launch {
            try {
                val uris = selectionItems.toVideoUris().map { it.toString() }
                mediaRepository.delete(uris)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    companion object {
        private const val SEARCH_DEBOUNCE_MS = 300L
    }
}

@Stable
data class SearchUiState(
    val query: String = "",
    val searchHistory: List<String> = emptyList(),
    val popularFolders: List<Folder> = emptyList(),
    val searchResults: SearchResults = SearchResults(),
    val isSearching: Boolean = false,
    val preferences: ApplicationPreferences = ApplicationPreferences(),
    val mediaInfo: dev.anilbeesetti.nextplayer.core.model.MediaInfo? = null,
    )

sealed interface SearchUiEvent {
    data class OnQueryChange(val query: String) : SearchUiEvent
    data class OnSearch(val query: String) : SearchUiEvent
    data class OnHistoryItemClick(val query: String) : SearchUiEvent
    data class OnRemoveHistoryItem(val query: String) : SearchUiEvent
    data object OnClearHistory : SearchUiEvent
    data class ShareSelectedItems(val selectionItems: Set<SelectionItem>)  : SearchUiEvent
    data class DeleteSelectedItems(val selectionItems: Set<SelectionItem>) : SearchUiEvent
    data class RenameVideo(val uri: Uri, val to: String) : SearchUiEvent
    data class ShowMediaInfo(val video: Video): SearchUiEvent
    data class ClearPlaybackHistory(val selectionItems: Set<SelectionItem>) : SearchUiEvent
    data object DismissMediaInfo : SearchUiEvent
}
