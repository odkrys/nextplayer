package dev.anilbeesetti.nextplayer.feature.player

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.anilbeesetti.nextplayer.core.common.Utils.formatDurationMillis
import dev.anilbeesetti.nextplayer.core.data.repository.MediaRepository
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.domain.GetSortedPlaylistUseCase
import dev.anilbeesetti.nextplayer.core.media.sync.MediaInfoSynchronizer
import dev.anilbeesetti.nextplayer.core.model.DrcPreset
import dev.anilbeesetti.nextplayer.core.model.LoopMode
import dev.anilbeesetti.nextplayer.core.model.PlayerPreferences
import dev.anilbeesetti.nextplayer.core.model.Video
import dev.anilbeesetti.nextplayer.core.model.VideoContentScale
import dev.anilbeesetti.nextplayer.feature.player.service.CastingService
import dev.anilbeesetti.nextplayer.feature.player.service.DlnaManager
import dev.anilbeesetti.nextplayer.feature.player.service.DlnaTransportState
import dev.anilbeesetti.nextplayer.feature.player.service.CastMediaSource
import dev.anilbeesetti.nextplayer.feature.player.service.PlayerService
import dev.anilbeesetti.nextplayer.feature.player.service.StopReason
import dev.anilbeesetti.nextplayer.feature.player.state.SubtitleOptionsEvent
import dev.anilbeesetti.nextplayer.feature.player.state.VideoZoomEvent
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val preferencesRepository: PreferencesRepository,
    private val getSortedPlaylistUseCase: GetSortedPlaylistUseCase,
    private val mediaInfoSynchronizer: MediaInfoSynchronizer,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    var playWhenReady: Boolean = true

    private val internalUiState = MutableStateFlow(
        PlayerUiState(
            playerPreferences = preferencesRepository.playerPreferences.value,
        ),
    )
    val uiState = internalUiState.asStateFlow()

    private val _currentVideo = MutableStateFlow<Video?>(null)
    val currentVideo = _currentVideo.asStateFlow()

    private var loadVideoInfoJob: Job? = null

    private val _currentVideoUri = MutableStateFlow<String?>(null)

    private val _dlnaDevices = MutableStateFlow<List<DlnaManager.DlnaDevice>>(emptyList())
    val dlnaDevices = _dlnaDevices.asStateFlow()

    private val _isDlnaSearching = MutableStateFlow(false)
    val isDlnaSearching = _isDlnaSearching.asStateFlow()

    private val _castingEvent = MutableSharedFlow<CastingEvent>()
    val castingEvent = _castingEvent.asSharedFlow()

    val dlnaPlaybackState = DlnaManager.playbackState
    private var autoStopJob: Job? = null

    var abRepeatA by mutableLongStateOf(C.TIME_UNSET)
    var abRepeatB by mutableLongStateOf(C.TIME_UNSET)

    init {
        viewModelScope.launch {
            preferencesRepository.playerPreferences.collect { prefs ->
                internalUiState.update { it.copy(playerPreferences = prefs) }
            }
        }

        viewModelScope.launch {
            DlnaManager.playbackState.collect { state ->
                when {
                    state.isActive && state.transportState == DlnaTransportState.STOPPED -> {
                        if (autoStopJob == null && !state.isManualTransition) {
                            autoStopJob = launch {
                                delay(1000)

                                val device = DlnaManager.currentDevice
                                if (device != null) {
                                    repeat(2) {
                                        val liveState = DlnaTransportState.fromString(DlnaManager.pollPlaybackState(device))
                                        if (liveState != DlnaTransportState.STOPPED) {
                                            autoStopJob = null
                                            return@launch
                                        }
                                        if (it < 1) delay(500)
                                    }
                                }

                                if (internalUiState.value.playerPreferences?.dlnaAutoplay == true) {
                                    if (PlayerService.instance?.hasNext() == true) {
                                        PlayerService.instance?.playNext(appContext)
                                            ?: _castingEvent.emit(CastingEvent.PlayNext)
                                    } else {
                                        stopCasting(appContext)
                                    }
                                } else {
                                    stopCasting(appContext)
                                }
                            }
                        }
                    }

                    state.isActive && (
                            state.transportState == DlnaTransportState.TRANSITIONING ||
                            state.transportState == DlnaTransportState.PLAYING
                    ) -> {
                        autoStopJob?.cancel()
                        autoStopJob = null
                    }

                    !state.isActive && state.stopReason == StopReason.DEVICE_UNREACHABLE -> {
                        autoStopJob?.cancel()
                        autoStopJob = null
                        _castingEvent.emit(CastingEvent.StopCasting)
                    }

                    !state.isActive -> {
                        autoStopJob?.cancel()
                        autoStopJob = null
                    }
                }
            }
        }
    }

    suspend fun getPlaylistFromUri(uri: Uri): List<Video> {
        return getSortedPlaylistUseCase.invoke(uri)
    }

    fun updateVideoZoom(uri: String, zoom: Float) {
        viewModelScope.launch {
            mediaRepository.updateMediumZoom(uri, zoom)
        }
    }

    fun updatePlayerBrightness(value: Float) {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences { it.copy(playerBrightness = value) }
        }
    }

    fun updateVideoContentScale(contentScale: VideoContentScale) {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences { it.copy(playerVideoZoom = contentScale) }
        }
    }

    fun setLoopMode(loopMode: LoopMode) {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences { it.copy(loopMode = loopMode) }
        }
    }

    fun setShuffleMode(shuffleMode: Boolean) {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences { it.copy(shuffleMode = shuffleMode) }
        }
    }

    fun onVideoZoomEvent(event: VideoZoomEvent) {
        when (event) {
            is VideoZoomEvent.ContentScaleChanged -> {
                updateVideoContentScale(event.contentScale)
            }
            is VideoZoomEvent.ZoomChanged -> {
                updateVideoZoom(event.mediaItem.mediaId, event.zoom)
            }
        }
    }

    fun onSubtitleOptionEvent(event: SubtitleOptionsEvent) {
        when (event) {
            is SubtitleOptionsEvent.UpdateSubtitleTextSize -> {
                updateSubtitleTextSize(event.size)
            }
            is SubtitleOptionsEvent.UpdateSubtitlePosition -> {
                updateSubtitlePosition(event.position)
            }
            is SubtitleOptionsEvent.DelayChanged -> {
                updateSubtitleDelay(event.mediaItem.mediaId, event.delay)
            }
            is SubtitleOptionsEvent.SpeedChanged -> {
                updateSubtitleSpeed(event.mediaItem.mediaId, event.speed)
            }
        }
    }

    private fun updateSubtitleDelay(uri: String, delay: Long) {
        viewModelScope.launch {
            mediaRepository.updateSubtitleDelay(uri, delay)
        }
    }

    private fun updateSubtitleSpeed(uri: String, speed: Float) {
        viewModelScope.launch {
            mediaRepository.updateSubtitleSpeed(uri, speed)
        }
    }

    private fun updateSubtitleTextSize(value: Int) {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences { it.copy(subtitleTextSize = value) }
        }
    }

    private fun updateSubtitlePosition(value: Float) {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences { it.copy(subtitlePosition = value) }
        }
    }

    fun updateCurrentVideoInfo(updatedVideo: Video?) {
        _currentVideo.value = updatedVideo
    }

    fun loadVideoInfo(uriString: String?) {
        if (uriString == null) return
        _currentVideoUri.value = uriString
        loadVideoInfoJob?.cancel()
        loadVideoInfoJob = viewModelScope.launch {
            mediaInfoSynchronizer.syncAndAwait(Uri.parse(uriString))
            if (_currentVideoUri.value != uriString) return@launch
            //_currentVideo.value = mediaRepository.getVideoByUri(uriString)

            val dbVideo = mediaRepository.getVideoByUri(uriString)
            val current = _currentVideo.value

            if (dbVideo != null) {
                val mergedDuration = current?.duration?.takeIf { it > 0 } ?: dbVideo.duration

                _currentVideo.value = dbVideo.copy(
                    duration = mergedDuration,
                    formattedDuration = if (mergedDuration > 0L) formatDurationMillis(mergedDuration) else dbVideo.formattedDuration,
                    videoStream = dbVideo.videoStream ?: current?.videoStream,
                    audioStreams = dbVideo.audioStreams.ifEmpty { current?.audioStreams ?: emptyList() },
                    subtitleStreams = dbVideo.subtitleStreams.ifEmpty { current?.subtitleStreams ?: emptyList() },
                )
            } else if (current == null) {
                _currentVideo.value = null
            }
        }
    }

    fun searchDlnaDevices(context: Context) {
        viewModelScope.launch {
            _isDlnaSearching.value = true
            _dlnaDevices.value = DlnaManager.searchDevices(context)
            _isDlnaSearching.value = false
        }
    }

    fun castToDevice(device: DlnaManager.DlnaDevice, uri: String, context: Context) {
        viewModelScope.launch {
            val service = PlayerService.instance ?: return@launch
            val source = service.resolveMediaSourceForUri(uri) ?: return@launch

            DlnaManager.startCasting(
                context = context,
                source = source,
                device = device,
                okHttpClient = service.okHttpClient,
                onSuccess = { Log.d("DLNA", "Started casting to ${device.name}") },
                onError = { Log.e("DLNA", it) }
            )
        }
    }

    fun stopCasting(context: Context) {
        context.startService(CastingService.stopIntent(context))
    }

    fun castCurrentMediaToActiveDevice(uri: String, context: Context) {
        val device = DlnaManager.currentDevice ?: return
        viewModelScope.launch {
            val service = PlayerService.instance ?: return@launch
            val source = service.resolveMediaSourceForUri(uri) ?: return@launch

            val mediaId = when (source) {
                is CastMediaSource.LocalFile -> source.file.absolutePath
                is CastMediaSource.RemoteUrl -> source.url
            }

            if (mediaId == DlnaManager.currentCastingPath) return@launch

            DlnaManager.updateCastingSource(context, source, device)
        }
    }

    fun seekCasting(positionMs: Long) {
        viewModelScope.launch {
            DlnaManager.currentDevice?.let { DlnaManager.seekTo(it, positionMs) }
        }
    }

    fun toggleCastingPlayPause() {
        viewModelScope.launch {
            val device = DlnaManager.currentDevice ?: return@launch
            if (dlnaPlaybackState.value.isPlaying) {
                DlnaManager.pause(device)
            } else {
                DlnaManager.play(device)
            }
        }
    }

    fun playPreviousCasting() {
        PlayerService.instance?.playPrevious(appContext)
            ?: viewModelScope.launch { _castingEvent.emit(CastingEvent.PlayPrevious) }
    }

    fun playNextCasting() {
        PlayerService.instance?.playNext(appContext)
            ?: viewModelScope.launch { _castingEvent.emit(CastingEvent.PlayNext) }
    }

    fun toggleDlnaAutoplay() {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences { currentPrefs ->
                currentPrefs.copy(dlnaAutoplay = !currentPrefs.dlnaAutoplay)
            }
        }
    }

    fun toggleDrc() {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences { currentPrefs ->
                currentPrefs.copy(enableDrc = !currentPrefs.enableDrc)
            }
        }
    }

    fun setDrcPreset(preset: DrcPreset) {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences { currentPrefs ->
                currentPrefs.copy(drcPreset = preset)
            }
        }
    }
}

@Stable
data class PlayerUiState(
    val playerPreferences: PlayerPreferences? = null,
)

sealed interface PlayerEvent

sealed interface CastingEvent {
    object PlayNext : CastingEvent
    object PlayPrevious : CastingEvent
    object StopCasting : CastingEvent
}
