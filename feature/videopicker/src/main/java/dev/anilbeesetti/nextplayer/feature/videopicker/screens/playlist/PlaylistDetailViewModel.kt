package dev.anilbeesetti.nextplayer.feature.videopicker.screens.playlist

import android.net.Uri
import androidx.compose.runtime.Stable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.anilbeesetti.nextplayer.core.data.remote.WebdavClient
import dev.anilbeesetti.nextplayer.core.data.repository.MediaRepository
import dev.anilbeesetti.nextplayer.core.data.repository.PlaylistRepository
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.data.repository.WebdavServerRepository
import dev.anilbeesetti.nextplayer.core.domain.playlist.AddMediumToPlaylistUseCase
import dev.anilbeesetti.nextplayer.core.domain.playlist.GetPlaylistWithMediaUseCase
import dev.anilbeesetti.nextplayer.core.domain.playlist.RemoveMediumFromPlaylistUseCase
import dev.anilbeesetti.nextplayer.core.domain.playlist.ReorderPlaylistUseCase
import dev.anilbeesetti.nextplayer.core.domain.playlist.UpdatePlaylistLastPlayedUseCase
import dev.anilbeesetti.nextplayer.core.model.ApplicationPreferences
import dev.anilbeesetti.nextplayer.core.model.LinkErrorType
import dev.anilbeesetti.nextplayer.core.model.Playlist
import dev.anilbeesetti.nextplayer.core.model.PlaylistSortOption
import dev.anilbeesetti.nextplayer.core.model.Video
import dev.anilbeesetti.nextplayer.core.ui.base.DataState
import dev.anilbeesetti.nextplayer.feature.videopicker.navigation.PlaylistArgs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.withPermit
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import java.net.URL

@HiltViewModel
class PlaylistDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getPlaylistWithMediaUseCase: GetPlaylistWithMediaUseCase,
    private val mediaRepository: MediaRepository,
    private val addMediumToPlaylistUseCase: AddMediumToPlaylistUseCase,
    private val removeMediumFromPlaylistUseCase: RemoveMediumFromPlaylistUseCase,
    private val reorderPlaylistUseCase: ReorderPlaylistUseCase,
    private val updatePlaylistLastPlayedUseCase: UpdatePlaylistLastPlayedUseCase,
    private val preferencesRepository: PreferencesRepository,
    private val playlistRepository: PlaylistRepository,
    private val webdavClient: WebdavClient,
    private val webdavServerRepository: WebdavServerRepository,
) : ViewModel() {

    private val playlistArgs = PlaylistArgs(savedStateHandle)
    val playlistId = playlistArgs.playlistId

    private val uiStateInternal = MutableStateFlow(PlaylistDetailUiState())
    val uiState = uiStateInternal.asStateFlow()

    private val _remotePlaybackProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val remotePlaybackProgress = _remotePlaybackProgress.asStateFlow()

    private val _remoteDurationMap = MutableStateFlow<Map<String, Long>>(emptyMap())
    val remoteDurationMap = _remoteDurationMap.asStateFlow()

    private val _isVerifyingLinks = MutableStateFlow(false)
    val isVerifyingLinks = _isVerifyingLinks.asStateFlow()

    private val _deadLinksFound = MutableStateFlow<List<DeadLink>>(emptyList())
    val deadLinksFound = _deadLinksFound.asStateFlow()

    private val _uiMessage = Channel<String>()
    val uiMessage = _uiMessage.receiveAsFlow()

    private val semaphore = Semaphore(10)

    init {
        viewModelScope.launch {
            combine(
                getPlaylistWithMediaUseCase(playlistId),
                mediaRepository.observeVideos(),
                preferencesRepository.applicationPreferences
            ) { playlist, allLocalVideos, prefs ->
                val videoMap = allLocalVideos.associateBy { it.uriString }
                val orderedUris = playlist?.mediaUris ?: emptyList()

                val orderedVideos = orderedUris.mapNotNull { uri ->
                    val localVideo = videoMap[uri]

                    if (localVideo != null) {
                        localVideo
                    } else if (uri.startsWith("http")) {
                        val extractedName = Uri.parse(uri).lastPathSegment ?: "Unknown"

                        Video(
                            id = 0L,
                            path = uri,
                            parentPath = "",
                            duration = 0L,
                            uriString = uri,
                            nameWithExtension = extractedName,
                            width = 0, height = 0, size = 0L,
                        )
                    } else {
                        null
                    }
                }

                Triple(playlist, orderedVideos, prefs)
            }.collect { (playlist, videos, prefs) ->
                uiStateInternal.update {
                    it.copy(
                        dataState = DataState.Success(playlist),
                        videos = videos,
                        preferences = prefs,
                        sortOption = playlist?.sortOption ?: PlaylistSortOption.ADDED_ASC,
                    )
                }
                refreshRemoteProgress(videos)
            }
        }
    }

    fun refreshRemoteProgress(videos: List<Video> = uiStateInternal.value.videos) {
        val remoteVideos = videos.filter { it.duration == 0L }
        if (remoteVideos.isEmpty()) {
            _remotePlaybackProgress.value = emptyMap()
            return
        }

        viewModelScope.launch {
            val progressMap = mutableMapOf<String, Float>()
            val durationMap = mutableMapOf<String, Long>()

            remoteVideos.forEach { video ->
                try {
                    val state = mediaRepository.getVideoState(video.uriString) ?: return@forEach
                    val position = state.position?.takeIf { it > 0 } ?: return@forEach
                    val duration = state.durationMs?.takeIf { it > 0 } ?: return@forEach

                    durationMap[video.uriString] = duration
                    progressMap[video.uriString] = (position.toFloat() / duration).coerceIn(0f, 1f)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            _remotePlaybackProgress.value = progressMap
            _remoteDurationMap.value = durationMap
        }
    }

    fun onEvent(event: PlaylistDetailUiEvent) {
        when (event) {
            is PlaylistDetailUiEvent.AddMedia -> addMedia(event.mediumUris)
            is PlaylistDetailUiEvent.RemoveMedium -> removeMedium(event.mediumUri)
            is PlaylistDetailUiEvent.ReorderMedia -> reorderMedia(event.orderedUris)
            is PlaylistDetailUiEvent.UpdateSortOption -> {
                viewModelScope.launch {
                    uiStateInternal.update { it.copy(sortOption = event.option) }
                    playlistRepository.updateSortOption(playlistId, event.option)
                }
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

    fun verifyWebdavLinks() {
        if (_isVerifyingLinks.value) return

        viewModelScope.launch {
            _isVerifyingLinks.value = true

            val webdavUrls = uiStateInternal.value.sortedFullUrls.filter { it.startsWith("http") }
            val servers = webdavServerRepository.getAllServers().first()

            val serverHostMap = servers.associateBy {
                val defaultPort = if (it.useSsl) 443 else 80
                val port = if (it.port == defaultPort) -1 else it.port
                val portSuffix = if (port != -1) ":$port" else ""
                "${it.host}$portSuffix"
            }

            val deadLinks = withContext(Dispatchers.IO) {
                webdavUrls.map { url ->
                    async {
                        semaphore.withPermit {
                            val hostKey = try {
                                val parsedUrl = URL(url)
                                val portSuffix = if (parsedUrl.port != -1) ":${parsedUrl.port}" else ""
                                "${parsedUrl.host}$portSuffix"
                            } catch (e: Exception) {
                                ""
                            }

                            val server = serverHostMap[hostKey]

                            if (server != null) {
                                val errorType = webdavClient.checkFile(server, url)
                                if (errorType != null) {
                                    DeadLink(url, errorType)
                                } else null
                            } else {
                                null
                            }
                        }
                    }
                }.awaitAll().filterNotNull()
            }

            _isVerifyingLinks.value = false

            if (deadLinks.isNotEmpty()) {
                _deadLinksFound.value = deadLinks
            } else {
                _uiMessage.send("All links are valid")
            }
        }
    }

    fun clearDeadLinksResult() {
        _deadLinksFound.value = emptyList()
    }

    fun removeDeadLinks() {
        val urisToRemove = _deadLinksFound.value
        if (urisToRemove.isEmpty()) return

        viewModelScope.launch {
            playlistRepository.removeDeletedMediaFromPlaylist(
                playlistId = playlistId,
                mediumUris = urisToRemove.map { it.url }
            )
            _deadLinksFound.value = emptyList()
        }
    }
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

    val sortedFullUrls: List<String>
        get() = sortedVideos.map { it.uriString }
}

data class DeadLink(
    val url: String,
    val errorType: LinkErrorType
)

sealed interface PlaylistDetailUiEvent {
    data class AddMedia(val mediumUris: List<String>) : PlaylistDetailUiEvent
    data class RemoveMedium(val mediumUri: String) : PlaylistDetailUiEvent
    data class ReorderMedia(val orderedUris: List<String>) : PlaylistDetailUiEvent
    data class UpdateSortOption(val option: PlaylistSortOption) : PlaylistDetailUiEvent
}
