package dev.anilbeesetti.nextplayer.feature.player.service

import android.app.PendingIntent
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Player.DISCONTINUITY_REASON_AUTO_TRANSITION
import androidx.media3.common.Player.DISCONTINUITY_REASON_REMOVE
import androidx.media3.common.Player.DISCONTINUITY_REASON_SEEK
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.session.CommandButton
import androidx.media3.session.CommandButton.ICON_UNDEFINED
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.size.Precision
import coil3.size.Scale
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.AndroidEntryPoint
import dev.anilbeesetti.nextplayer.core.common.extensions.deleteFiles
import dev.anilbeesetti.nextplayer.core.common.extensions.getFilenameFromUri
import dev.anilbeesetti.nextplayer.core.common.extensions.getLocalSubtitles
import dev.anilbeesetti.nextplayer.core.common.extensions.getPath
import dev.anilbeesetti.nextplayer.core.common.extensions.subtitleCacheDir
import dev.anilbeesetti.nextplayer.core.data.remote.WebdavClient
import dev.anilbeesetti.nextplayer.core.data.remote.WebdavResult
import dev.anilbeesetti.nextplayer.core.data.remote.setupUnsafeSsl
import dev.anilbeesetti.nextplayer.core.data.repository.MediaRepository
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.data.repository.WebdavServerRepository
import dev.anilbeesetti.nextplayer.core.domain.playlist.UpdatePlaylistLastPlayedUseCase
import dev.anilbeesetti.nextplayer.core.model.DecoderPriority
import dev.anilbeesetti.nextplayer.core.model.DrcPreset
import dev.anilbeesetti.nextplayer.core.model.LoopMode
import dev.anilbeesetti.nextplayer.core.model.PlayerPreferences
import dev.anilbeesetti.nextplayer.core.model.Resume
import dev.anilbeesetti.nextplayer.core.model.WebdavFile
import dev.anilbeesetti.nextplayer.core.ui.R as coreUiR
import dev.anilbeesetti.nextplayer.feature.player.PlayerActivity
import dev.anilbeesetti.nextplayer.feature.player.R
import dev.anilbeesetti.nextplayer.feature.player.extensions.DynamicRangeCompressor
import dev.anilbeesetti.nextplayer.feature.player.extensions.addAdditionalSubtitleConfiguration
import dev.anilbeesetti.nextplayer.feature.player.extensions.audioTrackIndex
import dev.anilbeesetti.nextplayer.feature.player.extensions.buildSubtitleUrisFromStream
import dev.anilbeesetti.nextplayer.feature.player.extensions.copy
import dev.anilbeesetti.nextplayer.feature.player.extensions.getManuallySelectedTrackIndex
import dev.anilbeesetti.nextplayer.feature.player.extensions.playbackSpeed
import dev.anilbeesetti.nextplayer.feature.player.extensions.positionMs
import dev.anilbeesetti.nextplayer.feature.player.extensions.setExtras
import dev.anilbeesetti.nextplayer.feature.player.extensions.setIsScrubbingModeEnabled
import dev.anilbeesetti.nextplayer.feature.player.extensions.subtitleDelayMilliseconds
import dev.anilbeesetti.nextplayer.feature.player.extensions.subtitleSpeed
import dev.anilbeesetti.nextplayer.feature.player.extensions.subtitleTrackIndex
import dev.anilbeesetti.nextplayer.feature.player.extensions.switchTrack
import dev.anilbeesetti.nextplayer.feature.player.extensions.uriToSubtitleConfiguration
import dev.anilbeesetti.nextplayer.feature.player.extensions.videoZoom
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.NextRenderersFactory
import io.github.anilbeesetti.nextlib.media3ext.renderer.subtitleDelayMilliseconds
import io.github.anilbeesetti.nextlib.media3ext.renderer.subtitleSpeed
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlin.collections.emptyList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

@OptIn(UnstableApi::class)
@AndroidEntryPoint
class PlayerService : MediaSessionService() {

    private val serviceScope: CoroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var mediaSession: MediaSession? = null
    private var artworkLoadJob: Job? = null

    @Inject
    lateinit var preferencesRepository: PreferencesRepository

    @Inject
    lateinit var mediaRepository: MediaRepository

    @Inject
    lateinit var imageLoader: ImageLoader

    @Inject
    lateinit var updatePlaylistLastPlayedUseCase: UpdatePlaylistLastPlayedUseCase

    @Inject
    lateinit var webdavServerRepository: WebdavServerRepository

    @Inject
    lateinit var webdavClient: WebdavClient

    @Inject
    lateinit var cacheDataSourceFactory: CacheDataSource.Factory

    internal lateinit var okHttpClient: OkHttpClient

    private val playerPreferences: PlayerPreferences
        get() = preferencesRepository.playerPreferences.value

    private val customCommands = CustomCommands.asSessionCommands()

    private var isMediaItemReady = false

    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var currentVolumeGain: Int = 0

    private var dynamicRangeCompressor: DynamicRangeCompressor? = null

    private var pendingSkipIntroMs: Long = 0L

    @Volatile
    private var pendingShuffleStartIndex: Int = C.INDEX_UNSET

    private val playbackStateListener = object : Player.Listener {
        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
            super.onTimelineChanged(timeline, reason)

            if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED && pendingShuffleStartIndex != C.INDEX_UNSET) {
                (mediaSession?.player as? ExoPlayer)?.let { exoPlayer ->
                    val length = timeline.windowCount
                    if (exoPlayer.shuffleModeEnabled && pendingShuffleStartIndex < length) {
                        val shuffledIndices = (listOf(pendingShuffleStartIndex) + (0 until length).filter { it != pendingShuffleStartIndex }.shuffled()).toIntArray()
                        exoPlayer.setShuffleOrder(
                            androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder(
                                shuffledIndices, System.currentTimeMillis()
                            )
                        )
                    }
                }
                pendingShuffleStartIndex = C.INDEX_UNSET
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            super.onMediaItemTransition(mediaItem, reason)

            pendingSkipIntroMs = 0L

            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) return
            isMediaItemReady = false
            loadArtworkForCurrentMediaItem()
            mediaItem?.mediaMetadata?.let { metadata ->
                mediaSession?.player?.run {
                    setPlaybackSpeed(metadata.playbackSpeed ?: playerPreferences.defaultPlaybackSpeed)
                    playerSpecificSubtitleDelayMilliseconds = metadata.subtitleDelayMilliseconds ?: 0L
                    playerSpecificSubtitleSpeed = metadata.subtitleSpeed ?: 1f
                }

                //metadata.positionMs?.takeIf { playerPreferences.resume == Resume.YES }?.let {
                //    mediaSession?.player?.seekTo(it)
                //}
                val resumePosition = metadata.positionMs?.takeIf { shouldResume(mediaItem?.mediaId) }
                if (resumePosition != null && resumePosition > 0L) {
                    mediaSession?.player?.seekTo(resumePosition)
                } else {
                    if (playerPreferences.enableSkipIntro) {
                        pendingSkipIntroMs = playerPreferences.skipIntroTime * 1000L
                    }
                }
            }

            val mediaId = mediaItem?.mediaId ?: return
            val playlistId = mediaItem.mediaMetadata.extras?.getLong("EXTRA_PLAYLIST_ID", -1L) ?: -1L

            if (playlistId != -1L) {
                serviceScope.launch {
                    updatePlaylistLastPlayedUseCase(playlistId, mediaId)
                }
            }
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            super.onPositionDiscontinuity(oldPosition, newPosition, reason)
            val oldMediaItem = oldPosition.mediaItem ?: return

            when (reason) {
                DISCONTINUITY_REASON_SEEK,
                DISCONTINUITY_REASON_AUTO_TRANSITION,
                -> {
                    if (newPosition.mediaItem == null || oldMediaItem == newPosition.mediaItem) return

                    //val updatedPosition = oldPosition.positionMs.takeIf { reason == DISCONTINUITY_REASON_SEEK } ?: C.TIME_UNSET
                    val updatedPosition = when (reason) { DISCONTINUITY_REASON_SEEK -> oldPosition.positionMs else -> 0L }
                    mediaSession?.player?.replaceMediaItem(
                        oldPosition.mediaItemIndex,
                        oldMediaItem.copy(positionMs = updatedPosition),
                    )
                    serviceScope.launch {
                        mediaRepository.updateMediumPosition(
                            uri = oldMediaItem.mediaId,
                            position = updatedPosition,
                        )
                    }
                }

                DISCONTINUITY_REASON_REMOVE -> {
                    serviceScope.launch {
                        val durationMs = oldMediaItem.mediaMetadata.durationMs
                        val isAtEnd = durationMs != null && oldPosition.positionMs >= durationMs - 1000
                        mediaRepository.updateMediumPosition(
                            uri = oldMediaItem.mediaId,
                            position = if (isAtEnd) C.TIME_UNSET else oldPosition.positionMs,
                        )
                    }
                }

                else -> return
            }
        }

        override fun onTracksChanged(tracks: Tracks) {
            super.onTracksChanged(tracks)
            if (!isMediaItemReady && tracks.groups.isNotEmpty()) {
                isMediaItemReady = true

                //if (!playerPreferences.rememberSelections) return
                if (!playerPreferences.rememberSelections) {
                    applySubtitleFallback(tracks)
                    return
                }

                mediaSession?.player?.mediaMetadata?.audioTrackIndex?.let {
                    mediaSession?.player?.switchTrack(C.TRACK_TYPE_AUDIO, it)
                }
                //mediaSession?.player?.mediaMetadata?.subtitleTrackIndex?.let {
                //    mediaSession?.player?.switchTrack(C.TRACK_TYPE_TEXT, it)

                val subtitleTrackIndex = mediaSession?.player?.mediaMetadata?.subtitleTrackIndex
                if (subtitleTrackIndex != null) {
                    mediaSession?.player?.switchTrack(C.TRACK_TYPE_TEXT, subtitleTrackIndex)
                } else {
                    applySubtitleFallback(tracks)
                }
            }
        }

        override fun onTrackSelectionParametersChanged(parameters: TrackSelectionParameters) {
            super.onTrackSelectionParametersChanged(parameters)
            val player = mediaSession?.player ?: return
            val currentMediaItem = player.currentMediaItem ?: return

            val audioTrackIndex = player.getManuallySelectedTrackIndex(C.TRACK_TYPE_AUDIO)
            val subtitleTrackIndex = player.getManuallySelectedTrackIndex(C.TRACK_TYPE_TEXT)

            if (audioTrackIndex != null) {
                serviceScope.launch {
                    mediaRepository.updateMediumAudioTrack(
                        uri = currentMediaItem.mediaId,
                        audioTrackIndex = audioTrackIndex,
                    )
                }
            }

            if (subtitleTrackIndex != null) {
                serviceScope.launch {
                    mediaRepository.updateMediumSubtitleTrack(
                        uri = currentMediaItem.mediaId,
                        subtitleTrackIndex = subtitleTrackIndex,
                    )
                }
            }

            player.replaceMediaItem(
                player.currentMediaItemIndex,
                currentMediaItem.copy(
                    audioTrackIndex = audioTrackIndex,
                    subtitleTrackIndex = subtitleTrackIndex,
                ),
            )
        }

        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
            super.onPlaybackParametersChanged(playbackParameters)
            val player = mediaSession?.player ?: return
            val currentMediaItem = player.currentMediaItem ?: return
            val playbackSpeed = playbackParameters.speed

            serviceScope.launch {
                mediaRepository.updateMediumPlaybackSpeed(
                    uri = currentMediaItem.mediaId,
                    playbackSpeed = playbackSpeed,
                )
            }
            player.replaceMediaItem(
                player.currentMediaItemIndex,
                currentMediaItem.copy(playbackSpeed = playbackSpeed),
            )
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            super.onPlaybackStateChanged(playbackState)

            if (playbackState == Player.STATE_ENDED || playbackState == Player.STATE_IDLE) {
                //mediaSession?.player?.trackSelectionParameters = TrackSelectionParameters.DEFAULT
                //mediaSession?.player?.setPlaybackSpeed(playerPreferences.defaultPlaybackSpeed)
                isMediaItemReady = false
            }

            if (playbackState == Player.STATE_READY) {
 /*
                mediaSession?.player?.let {
                    serviceScope.launch {
                        mediaRepository.updateMediumLastPlayedTime(
                            uri = it.currentMediaItem?.mediaId ?: return@launch,
                            lastPlayedTime = System.currentTimeMillis(),
                        )
                    }
                }
*/
                if (pendingSkipIntroMs > 0) {
                    if (playerPreferences.enableSkipIntro) {
                        val player = mediaSession?.player
                        val duration = player?.duration ?: C.TIME_UNSET

                        if (duration != C.TIME_UNSET) {
                            if (duration > pendingSkipIntroMs) {
                                player?.seekTo(pendingSkipIntroMs)
                            }
                            pendingSkipIntroMs = 0L
                        }
                    } else {
                        pendingSkipIntroMs = 0L
                    }
                }

                val player = mediaSession?.player ?: return
                val currentMediaItem = player.currentMediaItem ?: return
                val duration = player.duration.coerceAtLeast(0)
                val mediaId = currentMediaItem.mediaId
                val uri = android.net.Uri.parse(mediaId)
                val isRemote = uri.scheme == "http" || uri.scheme == "https"

                serviceScope.launch {
                    try {
                        mediaRepository.updateMediumLastPlayedTime(
                            uri = mediaId,
                            lastPlayedTime = System.currentTimeMillis(),
                        )

                        mediaRepository.updateMediumPosition(
                            uri = mediaId,
                            position = player.currentPosition,
                        )

                        if (isRemote && duration > 0 && duration != C.TIME_UNSET) {
                            mediaRepository.updateMediumDuration(
                                uri = mediaId,
                                durationMs = duration,
                            )
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                val exoPlayer = mediaSession?.player as? ExoPlayer ?: return
                val audioSessionId = exoPlayer.audioSessionId
                if (audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
                    val channelCount = exoPlayer.audioFormat?.channelCount?.takeIf { it > 0 } ?: 2

                    if (channelCount != dynamicRangeCompressor?.channelCount) {
                        if (exoPlayer.audioFormat == null) {
                            return
                        }

                        dynamicRangeCompressor?.release()
                        dynamicRangeCompressor = DynamicRangeCompressor(
                            audioSessionId = audioSessionId,
                            channelCount = channelCount,
                        ).apply {
                            enabled = playerPreferences.enableDrc
                            applyPreset(playerPreferences.drcPreset)
                        }
                    }
                }
            }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            super.onPlayWhenReadyChanged(playWhenReady, reason)

            if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM) {
                if (mediaSession?.player?.repeatMode != Player.REPEAT_MODE_OFF) {
                    mediaSession?.player?.seekTo(0)
                    mediaSession?.player?.play()
                    return
                }
                mediaSession?.run {
                    player.clearMediaItems()
                    player.stop()
                }
                stopSelf()
            }
        }

        override fun onRenderedFirstFrame() {
            super.onRenderedFirstFrame()
            val player = mediaSession?.player ?: return
            val currentMediaItem = player.currentMediaItem ?: return
            // Update the media metadata duration so that it will be used later in position discontinuity handling
            player.replaceMediaItem(
                player.currentMediaItemIndex,
                currentMediaItem.copy(durationMs = player.duration.coerceAtLeast(0))
            )
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            super.onIsPlayingChanged(isPlaying)
/*
            mediaSession?.run {
                serviceScope.launch {
                    mediaRepository.updateMediumPosition(
                        uri = player.currentMediaItem?.mediaId ?: return@launch,
                        position = player.currentPosition,
                    )
                }
*/
            mediaSession?.run {
                val currentMediaItem = player.currentMediaItem ?: return
                val mediaId = currentMediaItem.mediaId
                val duration = player.duration.coerceAtLeast(0)
                val uri = android.net.Uri.parse(mediaId)
                val isRemote = uri.scheme == "http" || uri.scheme == "https"

                serviceScope.launch {
                    try {
                        mediaRepository.updateMediumPosition(
                            uri = player.currentMediaItem?.mediaId ?: return@launch,
                            position = player.currentPosition,
                        )

                        if (isRemote && duration > 0 && duration != C.TIME_UNSET) {
                            mediaRepository.updateMediumDuration(
                                uri = mediaId,
                                durationMs = duration,
                            )
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            super.onRepeatModeChanged(repeatMode)
            serviceScope.launch {
                preferencesRepository.updatePlayerPreferences {
                    it.copy(
                        loopMode = when (repeatMode) {
                            Player.REPEAT_MODE_OFF -> LoopMode.OFF
                            Player.REPEAT_MODE_ONE -> LoopMode.ONE
                            Player.REPEAT_MODE_ALL -> LoopMode.ALL
                            else -> LoopMode.OFF
                        },
                    )
                }
            }
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            super.onShuffleModeEnabledChanged(shuffleModeEnabled)

            if (shuffleModeEnabled) {
                (mediaSession?.player as? ExoPlayer)?.let { exoPlayer ->
                    val currentIndex = exoPlayer.currentMediaItemIndex
                    val length = exoPlayer.mediaItemCount

                    if (length > 0 && currentIndex != C.INDEX_UNSET) {
                        val shuffledIndices = IntArray(length)
                        shuffledIndices[0] = currentIndex
                        val otherIndices = (0 until length).filter { it != currentIndex }.shuffled()
                        for (i in otherIndices.indices) {
                            shuffledIndices[i + 1] = otherIndices[i]
                        }
                        exoPlayer.setShuffleOrder(
                            androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder(
                                shuffledIndices, System.currentTimeMillis()
                            )
                        )
                    }
                }
            }

            serviceScope.launch {
                preferencesRepository.updatePlayerPreferences {
                    it.copy(shuffleMode = shuffleModeEnabled)
                }
            }
        }

        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            super.onAudioSessionIdChanged(audioSessionId)
/*
            if (!playerPreferences.enableVolumeBoost) return
            if (audioSessionId == C.AUDIO_SESSION_ID_UNSET) return
            try {
                loudnessEnhancer?.release()
                loudnessEnhancer = LoudnessEnhancer(audioSessionId)
                if (currentVolumeGain > 0) {
                    setEnhancerTargetGain(currentVolumeGain)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                loudnessEnhancer = null
            }
*/
            if (audioSessionId == C.AUDIO_SESSION_ID_UNSET) return

            if (playerPreferences.enableVolumeBoost) {
                try {
                    loudnessEnhancer?.release()
                    loudnessEnhancer = LoudnessEnhancer(audioSessionId)
                    if (currentVolumeGain > 0) {
                        setEnhancerTargetGain(currentVolumeGain)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    loudnessEnhancer = null
                }
            }

            val channelCount = (mediaSession?.player as? ExoPlayer)
                ?.audioFormat?.channelCount ?: 2

            dynamicRangeCompressor?.release()
            dynamicRangeCompressor = null

            dynamicRangeCompressor = DynamicRangeCompressor(
                audioSessionId = audioSessionId,
                channelCount = channelCount,
            ).apply {
                enabled = playerPreferences.enableDrc
                applyPreset(playerPreferences.drcPreset)

            }
        }
    }

    private fun setEnhancerTargetGain(gain: Int) {
        val enhancer = loudnessEnhancer ?: return

        try {
            enhancer.setTargetGain(gain)
            enhancer.enabled = gain > 0
            currentVolumeGain = enhancer.targetGain.toInt()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private val mediaSessionCallback = object : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val connectionResult = super.onConnect(session, controller)
            return MediaSession.ConnectionResult.accept(
                connectionResult.availableSessionCommands
                    .buildUpon()
                    .addSessionCommands(customCommands)
                    .build(),
                connectionResult.availablePlayerCommands,
            )
        }

        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = serviceScope.future(Dispatchers.Default) {

            pendingShuffleStartIndex = startIndex

            val updatedMediaItems = updatedMediaItemsWithMetadata(mediaItems)
            return@future MediaSession.MediaItemsWithStartPosition(updatedMediaItems, startIndex, startPositionMs)
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> = serviceScope.future(Dispatchers.Default) {
            val updatedMediaItems = updatedMediaItemsWithMetadata(mediaItems)
            return@future updatedMediaItems.toMutableList()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> = serviceScope.future {
            val command = CustomCommands.fromSessionCommand(customCommand)
                ?: return@future SessionResult(SessionError.ERROR_BAD_VALUE)

            when (command) {
                CustomCommands.ADD_SUBTITLE_TRACK -> {
                    val subtitleUri = args.getString(CustomCommands.SUBTITLE_TRACK_URI_KEY)?.toUri()
                        ?: return@future SessionResult(SessionError.ERROR_BAD_VALUE)

                    val newSubConfiguration = uriToSubtitleConfiguration(
                        uri = subtitleUri,
                        subtitleEncoding = playerPreferences.subtitleTextEncoding,
                    )
                    mediaSession?.player?.let { player ->
                        val currentMediaItem = player.currentMediaItem ?: return@let
                        val textTracks = player.currentTracks.groups.filter {
                            it.type == C.TRACK_TYPE_TEXT && it.isSupported
                        }

                        mediaRepository.updateMediumPosition(
                            uri = currentMediaItem.mediaId,
                            position = player.currentPosition,
                        )
                        mediaRepository.updateMediumSubtitleTrack(
                            uri = currentMediaItem.mediaId,
                            subtitleTrackIndex = textTracks.size,
                        )
                        mediaRepository.addExternalSubtitleToMedium(
                            uri = currentMediaItem.mediaId,
                            subtitleUri = subtitleUri,
                        )
                        player.addAdditionalSubtitleConfiguration(newSubConfiguration)
                    }
                    return@future SessionResult(SessionResult.RESULT_SUCCESS)
                }

                CustomCommands.SET_SKIP_INTRO_ENABLED -> {
                    val enabled = args.getBoolean(CustomCommands.SKIP_INTRO_ENABLED_KEY)
                    serviceScope.launch {
                        preferencesRepository.updatePlayerPreferences {
                            it.copy(enableSkipIntro = enabled)
                        }
                    }
                    return@future SessionResult(SessionResult.RESULT_SUCCESS)
                }

                CustomCommands.GET_SKIP_INTRO_ENABLED -> {
                    return@future SessionResult(
                        SessionResult.RESULT_SUCCESS,
                        Bundle().apply {
                            putBoolean(CustomCommands.SKIP_INTRO_ENABLED_KEY, playerPreferences.enableSkipIntro)
                        }
                    )
                }

                CustomCommands.SET_SKIP_INTRO_TIME -> {
                    val time = args.getInt(CustomCommands.SKIP_INTRO_TIME_KEY)
                    serviceScope.launch {
                        preferencesRepository.updatePlayerPreferences {
                            it.copy(skipIntroTime = time)
                        }
                    }
                    return@future SessionResult(SessionResult.RESULT_SUCCESS)
                }

                CustomCommands.GET_SKIP_INTRO_TIME -> {
                    return@future SessionResult(
                        SessionResult.RESULT_SUCCESS,
                        Bundle().apply {
                            putInt(CustomCommands.SKIP_INTRO_TIME_KEY, playerPreferences.skipIntroTime)
                        }
                    )
                }

                CustomCommands.SET_SKIP_SILENCE_ENABLED -> {
                    val enabled = args.getBoolean(CustomCommands.SKIP_SILENCE_ENABLED_KEY)
                    mediaSession?.player?.playerSpecificSkipSilenceEnabled = enabled
                    mediaSession?.sessionExtras = Bundle().apply {
                        putBoolean(CustomCommands.SKIP_SILENCE_ENABLED_KEY, enabled)
                    }
                    return@future SessionResult(SessionResult.RESULT_SUCCESS)
                }

                CustomCommands.GET_SKIP_SILENCE_ENABLED -> {
                    val enabled = mediaSession?.player?.playerSpecificSkipSilenceEnabled ?: false
                    return@future SessionResult(
                        SessionResult.RESULT_SUCCESS,
                        Bundle().apply {
                            putBoolean(CustomCommands.SKIP_SILENCE_ENABLED_KEY, enabled)
                        },
                    )
                }

                CustomCommands.SET_IS_SCRUBBING_MODE_ENABLED -> {
                    val enabled = args.getBoolean(CustomCommands.IS_SCRUBBING_MODE_ENABLED_KEY)
                    mediaSession?.player?.setIsScrubbingModeEnabled(enabled)
                    return@future SessionResult(SessionResult.RESULT_SUCCESS)
                }

                CustomCommands.IS_LOUDNESS_GAIN_SUPPORTED -> {
                    val isSupported = loudnessEnhancer != null
                    return@future SessionResult(
                        SessionResult.RESULT_SUCCESS,
                        Bundle().apply {
                            putBoolean(CustomCommands.IS_LOUDNESS_GAIN_SUPPORTED_KEY, isSupported)
                        },
                    )
                }

                CustomCommands.SET_LOUDNESS_GAIN -> {
                    val gain = args.getInt(CustomCommands.LOUDNESS_GAIN_KEY, 0)
                    setEnhancerTargetGain(gain)
                    return@future SessionResult(SessionResult.RESULT_SUCCESS)
                }

                CustomCommands.GET_LOUDNESS_GAIN -> {
                    return@future SessionResult(
                        SessionResult.RESULT_SUCCESS,
                        Bundle().apply {
                            putInt(CustomCommands.LOUDNESS_GAIN_KEY, currentVolumeGain)
                        },
                    )
                }

                CustomCommands.IS_DRC_SUPPORTED -> {
                    return@future SessionResult(
                        SessionResult.RESULT_SUCCESS,
                        Bundle().apply {
                            putBoolean(CustomCommands.IS_DRC_SUPPORTED_KEY, dynamicRangeCompressor?.isAvailable ?: false)
                        }
                    )
                }

                CustomCommands.SET_DRC_ENABLED -> {
                    val enabled = args.getBoolean(CustomCommands.DRC_ENABLED_KEY)
                    dynamicRangeCompressor?.enabled = enabled
                    return@future SessionResult(SessionResult.RESULT_SUCCESS)
                }

                CustomCommands.SET_DRC_PRESET -> {
                    val presetName = args.getString(CustomCommands.DRC_PRESET_KEY) ?: return@future SessionResult(SessionError.ERROR_BAD_VALUE)
                    val preset = runCatching { DrcPreset.valueOf(presetName) }.getOrNull()
                        ?: return@future SessionResult(SessionError.ERROR_BAD_VALUE)
                    dynamicRangeCompressor?.applyPreset(preset)
                    return@future SessionResult(SessionResult.RESULT_SUCCESS)
                }

                CustomCommands.GET_SUBTITLE_DELAY -> {
                    val subtitleDelay = mediaSession?.player?.playerSpecificSubtitleDelayMilliseconds ?: 0
                    return@future SessionResult(
                        SessionResult.RESULT_SUCCESS,
                        Bundle().apply {
                            putLong(CustomCommands.SUBTITLE_DELAY_KEY, subtitleDelay)
                        },
                    )
                }

                CustomCommands.SET_SUBTITLE_DELAY -> {
                    val subtitleDelay = args.getLong(CustomCommands.SUBTITLE_DELAY_KEY)
                    mediaSession?.player?.playerSpecificSubtitleDelayMilliseconds = subtitleDelay
                    return@future SessionResult(SessionResult.RESULT_SUCCESS)
                }

                CustomCommands.GET_SUBTITLE_SPEED -> {
                    val subtitleSpeed = mediaSession?.player?.playerSpecificSubtitleSpeed ?: 0f
                    return@future SessionResult(
                        SessionResult.RESULT_SUCCESS,
                        Bundle().apply {
                            putFloat(CustomCommands.SUBTITLE_SPEED_KEY, subtitleSpeed)
                        },
                    )
                }

                CustomCommands.SET_SUBTITLE_SPEED -> {
                    val subtitleSpeed = args.getFloat(CustomCommands.SUBTITLE_SPEED_KEY)
                    mediaSession?.player?.playerSpecificSubtitleSpeed = subtitleSpeed
                    return@future SessionResult(SessionResult.RESULT_SUCCESS)
                }

                CustomCommands.STOP_PLAYER_SESSION -> {
                    mediaSession?.run {
                        serviceScope.launch {
                            mediaRepository.updateMediumPosition(
                                uri = player.currentMediaItem?.mediaId ?: return@launch,
                                position = player.currentPosition,
                            )
                        }
                    }
                    mediaSession?.run {
                        player.clearMediaItems()
                        player.stop()
                    }
                    stopSelf()
                    return@future SessionResult(SessionResult.RESULT_SUCCESS)
                }
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    companion object {
        var instance: PlayerService? = null

        private val webdavCredentials = mutableMapOf<String, String>()

        fun setWebdavCredentials(host: String, auth: String) {
            webdavCredentials[host] = auth
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        serviceScope.launch(Dispatchers.IO) {
            try {
                webdavServerRepository.getAllServers().collectLatest { servers ->
                    servers.forEach { server ->
                        if (server.username.isNotEmpty()) {
                            val auth = okhttp3.Credentials.basic(server.username, server.password)
                            webdavCredentials[server.host] = auth
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val renderersFactory = NextRenderersFactory(applicationContext)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(
                when (playerPreferences.decoderPriority) {
                    DecoderPriority.DEVICE_ONLY -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF
                    DecoderPriority.PREFER_DEVICE -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
                    DecoderPriority.PREFER_APP -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
                },
            )

        val trackSelector = DefaultTrackSelector(applicationContext).apply {
            setParameters(
                buildUponParameters()
                    .setPreferredAudioLanguage(playerPreferences.preferredAudioLanguage)
                    .setPreferredTextLanguage(playerPreferences.preferredSubtitleLanguage)
                    .setSelectUndeterminedTextLanguage(true),
            )
        }

        okHttpClient = OkHttpClient.Builder()
            .setupUnsafeSsl()
            .addInterceptor { chain ->
                val request = chain.request()
                val host = request.url.host
                val auth = webdavCredentials[host]

                if (auth != null) {
                    chain.proceed(
                        request.newBuilder()
                            .header("Authorization", auth)
                            .build()
                    )
                } else {
                    chain.proceed(request)
                }
            }
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

        val okHttpDataSourceFactory = OkHttpDataSource.Factory(okHttpClient)

        val cacheSizeMb = preferencesRepository.applicationPreferences.value.diskCacheSizeMb
        val defaultDataSourceFactory = if (cacheSizeMb > 0) {
            val configuredCacheFactory = cacheDataSourceFactory
                .setUpstreamDataSourceFactory(okHttpDataSourceFactory)
            DefaultDataSource.Factory(applicationContext, configuredCacheFactory)
        } else {
            DefaultDataSource.Factory(applicationContext, okHttpDataSourceFactory)
        }

        val player = ExoPlayer.Builder(applicationContext)
            .setRenderersFactory(renderersFactory)
            .setTrackSelector(trackSelector)
            .setMediaSourceFactory(DefaultMediaSourceFactory(applicationContext).setDataSourceFactory(defaultDataSourceFactory))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                playerPreferences.requireAudioFocus,
            )
            .setHandleAudioBecomingNoisy(playerPreferences.pauseOnHeadsetDisconnect)
            .build()
            .also {
                it.addListener(playbackStateListener)
                it.pauseAtEndOfMediaItems = !playerPreferences.autoplay
                it.repeatMode = when (playerPreferences.loopMode) {
                    LoopMode.OFF -> Player.REPEAT_MODE_OFF
                    LoopMode.ONE -> Player.REPEAT_MODE_ONE
                    LoopMode.ALL -> Player.REPEAT_MODE_ALL
                }
                it.shuffleModeEnabled = playerPreferences.shuffleMode
            }

        try {
            mediaSession = MediaSession.Builder(this, player).apply {
                setSessionActivity(
                    PendingIntent.getActivity(
                        this@PlayerService,
                        0,
                        Intent(this@PlayerService, PlayerActivity::class.java),
                        PendingIntent.FLAG_IMMUTABLE,
                    ),
                )
                setCallback(mediaSessionCallback)
                setCustomLayout(
                    listOf(
                        CommandButton.Builder(ICON_UNDEFINED)
                            .setCustomIconResId(coreUiR.drawable.ic_close)
                            .setDisplayName(getString(coreUiR.string.stop_player_session))
                            .setSessionCommand(CustomCommands.STOP_PLAYER_SESSION.sessionCommand)
                            .setEnabled(true)
                            .build(),
                    ),
                )
            }.build()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player!!
        if (!player.playWhenReady || player.mediaItemCount == 0 || player.playbackState == Player.STATE_ENDED) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        artworkLoadJob?.cancel()
        loudnessEnhancer?.release()
        loudnessEnhancer = null
        dynamicRangeCompressor?.release()
        dynamicRangeCompressor = null
        mediaSession?.run {
            player.clearMediaItems()
            player.stop()
            player.removeListener(playbackStateListener)
            player.release()
            release()
            mediaSession = null
        }
        //subtitleCacheDir.deleteFiles()
        //serviceScope.cancel()
        serviceScope.launch {
            DlnaManager.stopCasting(applicationContext)
        }.invokeOnCompletion {
            DlnaManager.release()
            subtitleCacheDir.deleteFiles()
            serviceScope.cancel()
        }
    }

    private suspend fun updatedMediaItemsWithMetadata(
        mediaItems: List<MediaItem>,
    ): List<MediaItem> = supervisorScope {

        val servers = webdavServerRepository.getAllServers().first()
        val directoryCache = ConcurrentHashMap<String, Deferred<List<WebdavFile>>>()

        mediaItems.map { mediaItem ->
            async {
                val uri = mediaItem.mediaId.toUri()
                val video = mediaRepository.getVideoByUri(uri = mediaItem.mediaId)
                val videoState = mediaRepository.getVideoState(uri = mediaItem.mediaId)

                val existingPlaylistId = mediaItem.mediaMetadata.extras?.getLong("EXTRA_PLAYLIST_ID", -1L) ?: -1L

                val externalSubs = videoState?.externalSubs ?: emptyList()
/*
                val localSubs = (videoState?.path ?: getPath(uri))?.let {
                    File(it).getLocalSubtitles(
                        context = this@PlayerService,
                        excludeSubsList = externalSubs,
                    )
                } ?: emptyList()
*/
                val filePath = getPath(uri)
                val localSubs = if (filePath != null) {
                    File(filePath).getLocalSubtitles(
                        context = this@PlayerService,
                        excludeSubsList = externalSubs,
                    )
                } else {
                    emptyList()
                }

                val existingSubConfigurations = mediaItem.localConfiguration?.subtitleConfigurations ?: emptyList()

                val sourceUri = mediaItem.localConfiguration?.uri ?: uri

                val remoteSubConfigurations = if (filePath == null && (uri.scheme == "http" || uri.scheme == "https")) {
                    val host = uri.host ?: ""
                    val port = if (uri.port != -1) uri.port else if (uri.scheme == "https") 443 else 80

                    val server = servers.firstOrNull { it.host == host && it.port == port }

                    if (server != null) {
                        val serverBasePath = Uri.parse(server.baseUrl).path?.trimEnd('/') ?: ""
                        val uriPath = uri.path ?: ""

                        val relativePath = if (serverBasePath.isNotEmpty() && uriPath.startsWith(serverBasePath)) {
                            uriPath.substring(serverBasePath.length)
                        } else {
                            uriPath
                        }

                        val parentPath = relativePath.substringBeforeLast("/").ifEmpty { "/" }

                        val deferred = directoryCache.computeIfAbsent(parentPath) {
                            async {
                                webdavClient.listFiles(server, parentPath)
                                    .let { (it as? WebdavResult.Success)?.data } ?: emptyList()
                            }
                        }

                        val files = deferred.await()

                        val videoBaseName = Uri.decode(uri.lastPathSegment ?: "")
                            .substringBeforeLast(".")

                        val subExtensions = listOf("srt", "ssa", "ass", "vtt", "ttml", "xml", "dfxp")

                        files.filter { file ->
                            !file.isDirectory &&
                                    file.name.substringBeforeLast(".") == videoBaseName &&
                                    file.name.substringAfterLast(".").lowercase() in subExtensions
                        }
                        .sortedBy { file ->
                            subExtensions.indexOf(file.name.substringAfterLast(".").lowercase())
                        }
                        .map { subFile ->
                            val encodedPath = Uri.encode(subFile.path.trimStart('/'), "/")
                            val subUrl = "${server.baseUrl.trimEnd('/')}/$encodedPath"

                            uriToSubtitleConfiguration(
                                uri = Uri.parse(subUrl),
                                subtitleEncoding = playerPreferences.subtitleTextEncoding,
                            )
                        }
                    } else {
                        val remoteUris = buildSubtitleUrisFromStream(uri, okHttpClient)

                        remoteUris.map { subUri ->
                            uriToSubtitleConfiguration(
                                uri = subUri,
                                subtitleEncoding = playerPreferences.subtitleTextEncoding,
                            )
                        }
                    }
                } else {
                    emptyList()
                }

                val subConfigurations = (localSubs + externalSubs).map { subtitleUri ->
                    uriToSubtitleConfiguration(
                        uri = subtitleUri,
                        subtitleEncoding = playerPreferences.subtitleTextEncoding,
                    )
                } + remoteSubConfigurations

                // Use placeholder artwork initially - actual artwork will be loaded in background
                val artworkUri = getDefaultArtworkUri()

                val title = mediaItem.mediaMetadata.title ?: video?.nameWithExtension ?: getFilenameFromUri(uri)
                val positionMs = mediaItem.mediaMetadata.positionMs ?: videoState?.position
                val videoScale = mediaItem.mediaMetadata.videoZoom ?: videoState?.videoScale
                val playbackSpeed = mediaItem.mediaMetadata.playbackSpeed ?: videoState?.playbackSpeed
                val audioTrackIndex = mediaItem.mediaMetadata.audioTrackIndex ?: videoState?.audioTrackIndex
                val subtitleTrackIndex = mediaItem.mediaMetadata.subtitleTrackIndex ?: videoState?.subtitleTrackIndex
                val subtitleDelay = mediaItem.mediaMetadata.subtitleDelayMilliseconds ?: videoState?.subtitleDelayMilliseconds
                val subtitleSpeed = mediaItem.mediaMetadata.subtitleSpeed ?: videoState?.subtitleSpeed
/*
                mediaItem.buildUpon().apply {
                    setSubtitleConfigurations(existingSubConfigurations + subConfigurations)
                    setMediaMetadata(
                        MediaMetadata.Builder().apply {
                            setTitle(title)
                            setArtworkUri(artworkUri)
                            setExtras(
                                positionMs = positionMs,
                                videoScale = videoScale,
                                playbackSpeed = playbackSpeed,
                                audioTrackIndex = audioTrackIndex,
                                subtitleTrackIndex = subtitleTrackIndex,
                                subtitleDelayMilliseconds = subtitleDelay,
                                subtitleSpeed = subtitleSpeed,
                            )
                        }.build(),
                    )
                }.build()
            }
        }.awaitAll()
 */
                val metadata = MediaMetadata.Builder().apply {
                    setTitle(title)
                    setArtworkUri(artworkUri)
                    setExtras(
                        positionMs = positionMs,
                        videoScale = videoScale,
                        playbackSpeed = playbackSpeed,
                        audioTrackIndex = audioTrackIndex,
                        subtitleTrackIndex = subtitleTrackIndex,
                        subtitleDelayMilliseconds = subtitleDelay,
                        subtitleSpeed = subtitleSpeed,
                    )
                }.build()

                val finalMetadata = metadata.buildUpon().setExtras(
                    Bundle(metadata.extras ?: Bundle()).apply {
                        putLong("EXTRA_PLAYLIST_ID", existingPlaylistId)
                    }
                ).build()

                mediaItem.buildUpon().apply {
                    setSubtitleConfigurations(existingSubConfigurations + subConfigurations)
                    setMediaMetadata(finalMetadata)
                }.build()
            }
        }.awaitAll()
    }

    private fun getDefaultArtworkUri(): Uri = Uri.Builder().apply {
        val defaultArtwork = R.drawable.artwork_default
        scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
        authority(resources.getResourcePackageName(defaultArtwork))
        appendPath(resources.getResourceTypeName(defaultArtwork))
        appendPath(resources.getResourceEntryName(defaultArtwork))
    }.build()

    private fun loadArtworkForCurrentMediaItem() {
        artworkLoadJob?.cancel()
        artworkLoadJob = serviceScope.launch(Dispatchers.Main) {
            val player = mediaSession?.player ?: return@launch
            val currentMediaItem = player.currentMediaItem ?: return@launch
            if (currentMediaItem.mediaMetadata.artworkData != null) return@launch

            val artworkUri = loadArtworkForMediaItem(currentMediaItem) ?: return@launch

            val updatedPlayer = mediaSession?.player ?: return@launch
            val updatedMediaItem = updatedPlayer.currentMediaItem ?: return@launch
            if (updatedMediaItem.mediaId != currentMediaItem.mediaId) return@launch

            updatedPlayer.replaceMediaItem(
                updatedPlayer.currentMediaItemIndex,
                updatedMediaItem.withArtwork(artworkUri),
            )
        }
    }

    private suspend fun loadArtworkForMediaItem(mediaItem: MediaItem): Uri? = withContext(Dispatchers.IO) {
        val uri = mediaItem.mediaId.toUri()
        return@withContext try {
            val request = ImageRequest.Builder(this@PlayerService)
                .data(uri)
                .size(512, 512)
                .scale(Scale.FILL)
                .precision(Precision.INEXACT)
                .build()
            imageLoader.execute(request)
            val diskCache = imageLoader.diskCache ?: return@withContext null
            return@withContext diskCache.openSnapshot(uri.toString())?.use { snapshot ->
                snapshot.data.toFile().toUri()
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun MediaItem.withArtwork(uri: Uri): MediaItem = buildUpon()
        .setMediaMetadata(
            mediaMetadata.buildUpon()
                .setArtworkUri(uri)
                .build(),
        )
        .build()

    private suspend fun resolveMediaSourceFromPlayer(): CastMediaSource? {
        val uriString = mediaSession?.player?.currentMediaItem?.mediaId ?: return null

        return resolveMediaSource(
            uri = uriString,
            authHeaders = webdavCredentials,
            okHttpClient = okHttpClient,
            getVideoPath = {
                mediaRepository.getVideoByUri(it)?.path
                    ?: applicationContext.getPath(it.toUri())
            }
        )
    }

    suspend fun resolveMediaSourceForUri(uri: String): CastMediaSource? {
        return resolveMediaSource(
            uri = uri,
            authHeaders = webdavCredentials,
            okHttpClient = okHttpClient,
            getVideoPath = {
                mediaRepository.getVideoByUri(it)?.path
                    ?: applicationContext.getPath(it.toUri())
            }
        )
    }

    private suspend fun updateCastingToCurrentItem(context: Context) {
        val device = DlnaManager.currentDevice ?: return
        val source = resolveMediaSourceFromPlayer() ?: return
        DlnaManager.updateCastingSource(context, source, device)
    }

    fun playNext(context: Context) {
        val player = mediaSession?.player ?: return
        if (!player.hasNextMediaItem()) return

        player.seekToNext()
        player.pause()

        serviceScope.launch {
            delay(100)
            if (DlnaManager.currentDevice != null) {
                DlnaManager.ensureCastingLocks(context)
            }
            updateCastingToCurrentItem(context)
        }
    }

    fun playPrevious(context: Context) {
        val player = mediaSession?.player ?: return
        if (!player.hasPreviousMediaItem()) return

        player.seekToPrevious()
        player.pause()

        serviceScope.launch {
            delay(100)
            updateCastingToCurrentItem(context)
        }
    }

    fun hasNext(): Boolean {
        return mediaSession?.player?.hasNextMediaItem() ?: false
    }

    private fun shouldResume(mediaId: String?): Boolean {
        val uriString = mediaId ?: return false
        val uri = android.net.Uri.parse(uriString)

        val isHttp = uri.scheme == "http" || uri.scheme == "https"
        val isWebdavHost = uri.host?.let { webdavCredentials.containsKey(it) } == true
        val isRemote = isHttp && isWebdavHost

        return when (playerPreferences.resume) {
            Resume.YES         -> true
            Resume.LOCAL_ONLY  -> !isRemote
            Resume.REMOTE_ONLY -> isRemote
            Resume.NO          -> false
        }
    }

    private fun applySubtitleFallback(tracks: Tracks) {
        val player = mediaSession?.player ?: return
        val preferredLang = playerPreferences.preferredSubtitleLanguage.takeIf { it.isNotBlank() }

        val textGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
        if (textGroups.isEmpty()) return

        if (textGroups.any { it.isSelected && it.isSupported }) return

        val bestTrackIndex = textGroups
            .withIndex()
            .filter { it.value.isSupported }
            .minByOrNull { (_, group) ->
                val lang = group.getTrackFormat(0).language
                when {
                    preferredLang != null && lang == preferredLang -> 0
                    lang == "und" || lang == null -> 1
                    else -> 2
                }
            }?.index ?: return

        player.switchTrack(C.TRACK_TYPE_TEXT, bestTrackIndex)
    }
}

@get:UnstableApi
@set:UnstableApi
private var Player.playerSpecificSkipSilenceEnabled: Boolean
    @OptIn(UnstableApi::class)
    get() = when (this) {
        is ExoPlayer -> this.skipSilenceEnabled
        else -> false
    }
    set(value) {
        when (this) {
            is ExoPlayer -> this.skipSilenceEnabled = value
        }
    }

@get:UnstableApi
@set:UnstableApi
private var Player.playerSpecificSubtitleDelayMilliseconds: Long
    @OptIn(UnstableApi::class)
    get() = when (this) {
        is ExoPlayer -> this.subtitleDelayMilliseconds
        else -> 0L
    }
    set(value) {
        when (this) {
            is ExoPlayer -> this.subtitleDelayMilliseconds = value
        }
    }

@get:UnstableApi
@set:UnstableApi
private var Player.playerSpecificSubtitleSpeed: Float
    @OptIn(UnstableApi::class)
    get() = when (this) {
        is ExoPlayer -> this.subtitleSpeed
        else -> 0f
    }
    set(value) {
        when (this) {
            is ExoPlayer -> this.subtitleSpeed = value
        }
    }
