package dev.anilbeesetti.nextplayer.feature.player

import android.content.res.Configuration
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import dev.anilbeesetti.nextplayer.core.model.ControlButtonsPosition
import dev.anilbeesetti.nextplayer.core.model.PlayerPreferences
import dev.anilbeesetti.nextplayer.core.ui.composables.VideoInfoDialog
import dev.anilbeesetti.nextplayer.core.ui.R as coreUiR
import dev.anilbeesetti.nextplayer.core.ui.extensions.copy
import dev.anilbeesetti.nextplayer.feature.player.buttons.AbRepeatPanelOverlay
import dev.anilbeesetti.nextplayer.feature.player.buttons.DlnaCastButton
import dev.anilbeesetti.nextplayer.feature.player.buttons.NextButton
import dev.anilbeesetti.nextplayer.feature.player.buttons.PlayPauseButton
import dev.anilbeesetti.nextplayer.feature.player.buttons.PlayerButton
import dev.anilbeesetti.nextplayer.feature.player.buttons.PreviousButton
import dev.anilbeesetti.nextplayer.feature.player.extensions.remoteVideoInfo
import dev.anilbeesetti.nextplayer.feature.player.extensions.nameRes
import dev.anilbeesetti.nextplayer.feature.player.state.ControlsVisibilityState
import dev.anilbeesetti.nextplayer.feature.player.state.VerticalGesture
import dev.anilbeesetti.nextplayer.feature.player.state.rememberBrightnessState
import dev.anilbeesetti.nextplayer.feature.player.state.rememberControlsVisibilityState
import dev.anilbeesetti.nextplayer.feature.player.state.rememberErrorState
import dev.anilbeesetti.nextplayer.feature.player.state.rememberMediaPresentationState
import dev.anilbeesetti.nextplayer.feature.player.state.rememberMetadataState
import dev.anilbeesetti.nextplayer.feature.player.state.rememberPictureInPictureState
import dev.anilbeesetti.nextplayer.feature.player.state.rememberRotationState
import dev.anilbeesetti.nextplayer.feature.player.state.rememberSeekGestureState
import dev.anilbeesetti.nextplayer.feature.player.state.rememberTapGestureState
import dev.anilbeesetti.nextplayer.feature.player.state.rememberVideoZoomAndContentScaleState
import dev.anilbeesetti.nextplayer.feature.player.state.rememberVolumeAndBrightnessGestureState
import dev.anilbeesetti.nextplayer.feature.player.state.rememberVolumeState
import dev.anilbeesetti.nextplayer.feature.player.state.seekAmountFormatted
import dev.anilbeesetti.nextplayer.feature.player.state.seekToPositionFormated
import dev.anilbeesetti.nextplayer.feature.player.ui.CastingControllerView
import dev.anilbeesetti.nextplayer.feature.player.ui.DoubleTapIndicator
import dev.anilbeesetti.nextplayer.feature.player.ui.OverlayShowView
import dev.anilbeesetti.nextplayer.feature.player.ui.OverlayView
import dev.anilbeesetti.nextplayer.feature.player.ui.SubtitleConfiguration
import dev.anilbeesetti.nextplayer.feature.player.ui.VerticalProgressView
import dev.anilbeesetti.nextplayer.feature.player.ui.controls.ControlsBottomView
import dev.anilbeesetti.nextplayer.feature.player.ui.controls.ControlsTopView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

val LocalControlsVisibilityState = compositionLocalOf<ControlsVisibilityState?> { null }

@OptIn(UnstableApi::class)
@Composable
fun MediaPlayerScreen(
    player: Player?,
    viewModel: PlayerViewModel,
    playerPreferences: PlayerPreferences,
    modifier: Modifier = Modifier,
    onSelectSubtitleClick: () -> Unit,
    onBackClick: () -> Unit,
    onPlayInBackgroundClick: () -> Unit,
) {
    val volumeState = rememberVolumeState(
        player = player,
        showVolumePanelIfHeadsetIsOn = playerPreferences.showSystemVolumePanel,
    )
    player ?: return
    val metadataState = rememberMetadataState(player)
    val mediaPresentationState = rememberMediaPresentationState(player)
    val controlsVisibilityState = rememberControlsVisibilityState(
        player = player,
        hideAfter = playerPreferences.controllerAutoHideTimeout.seconds,
    )
    val tapGestureState = rememberTapGestureState(
        player = player,
        doubleTapGesture = playerPreferences.doubleTapGesture,
        seekIncrementMillis = playerPreferences.seekIncrement.seconds.inWholeMilliseconds,
        //useLongPressGesture = playerPreferences.useLongPressControls,
        longPressGesture = playerPreferences.longPressGesture,
        longPressSpeed = playerPreferences.longPressControlsSpeed,
    )
    val seekGestureState = rememberSeekGestureState(
        player = player,
        sensitivity = playerPreferences.seekSensitivity,
        enableSeekGesture = playerPreferences.useSeekControls,
        onSeekStart = {
            if (controlsVisibilityState.controlsVisible) {
                controlsVisibilityState.keepVisible()
            }
        },
        onSeekFinished = { controlsVisibilityState.releaseKeepVisible() },
    )
    /*
    val pictureInPictureState = rememberPictureInPictureState(
        player = player,
        autoEnter = playerPreferences.autoPip,
    )
    */
    val videoZoomAndContentScaleState = rememberVideoZoomAndContentScaleState(
        player = player,
        initialContentScale = playerPreferences.playerVideoZoom,
        enableZoomGesture = playerPreferences.useZoomControls,
        enablePanGesture = playerPreferences.enablePanGesture,
        onEvent = viewModel::onVideoZoomEvent,
    )
    val pictureInPictureState = rememberPictureInPictureState(
        player = player,
        autoEnter = playerPreferences.autoPip,
        onEnterPip = { videoZoomAndContentScaleState.resetZoomAndOffset(showIndicator = false) }
    )
    val brightnessState = rememberBrightnessState()
    val volumeAndBrightnessGestureState = rememberVolumeAndBrightnessGestureState(
        volumeState = volumeState,
        brightnessState = brightnessState,
        enableVolumeGesture = playerPreferences.enableVolumeSwipeGesture,
        enableBrightnessGesture = playerPreferences.enableBrightnessSwipeGesture,
        volumeGestureSensitivity = playerPreferences.volumeGestureSensitivity,
        brightnessGestureSensitivity = playerPreferences.brightnessGestureSensitivity,
    )
    val rotationState = rememberRotationState(
        player = player,
        screenOrientation = playerPreferences.playerScreenOrientation,
    )
    val errorState = rememberErrorState(player = player)

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    var currentPositionMs by remember { mutableLongStateOf(0L) }

    val abRepeatA = viewModel.abRepeatA
    val abRepeatB = viewModel.abRepeatB
    var showAbRepeatPanel by remember { mutableStateOf(false) }

    LaunchedEffect(player) {
        if (player == null) return@LaunchedEffect

        while (true) {
            currentPositionMs = player.currentPosition

            val skipIntroMs = playerPreferences.skipIntroTime * 1000L
            val isInIntro = skipIntroMs > 0 && currentPositionMs < skipIntroMs

            if (controlsVisibilityState.controlsVisible || isInIntro) {
                delay(500L)
            } else {
                delay(2000L)
            }
        }
    }

    LaunchedEffect(pictureInPictureState.isInPictureInPictureMode) {
        if (pictureInPictureState.isInPictureInPictureMode) {
            controlsVisibilityState.hideControls()
        }
    }

    LaunchedEffect(tapGestureState.isLongPressGestureInAction) {
        if (tapGestureState.isLongPressGestureInAction) {
            controlsVisibilityState.hideControls()
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        if (playerPreferences.rememberPlayerBrightness) {
            brightnessState.setBrightness(playerPreferences.playerBrightness)
        }
    }

    LaunchedEffect(brightnessState.currentBrightness) {
        if (playerPreferences.rememberPlayerBrightness) {
            viewModel.updatePlayerBrightness(brightnessState.currentBrightness)
        }
    }

    LaunchedEffect(player.currentMediaItem) {
        viewModel.loadVideoInfo(player.currentMediaItem?.mediaId)
    }

    var overlayView by remember { mutableStateOf<OverlayView?>(null) }
    val currentVideo by viewModel.currentVideo.collectAsStateWithLifecycle()
    var showVideoInfoDialog by remember { mutableStateOf(false) }

    val dlnaDevices by viewModel.dlnaDevices.collectAsStateWithLifecycle()
    val isDlnaSearching by viewModel.isDlnaSearching.collectAsStateWithLifecycle()
    val currentUri = player.currentMediaItem?.mediaId
    val dlnaPlaybackState by viewModel.dlnaPlaybackState.collectAsStateWithLifecycle()
    var isDlnaMenuExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val isCastingActiveRef = rememberUpdatedState(dlnaPlaybackState.isActive)
    val coroutineScope = rememberCoroutineScope()

    DisposableEffect(player) {
        var lastMediaId: String? = player.currentMediaItem?.mediaId
        val listener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val uri = mediaItem?.mediaId ?: return
                if (uri != lastMediaId) {
                    viewModel.loadVideoInfo(uri)

                    viewModel.abRepeatA = C.TIME_UNSET
                    viewModel.abRepeatB = C.TIME_UNSET
                    showAbRepeatPanel = false

                    lastMediaId = uri
                }

                if (isCastingActiveRef.value &&
                    reason != Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT
                ) {
                    viewModel.castCurrentMediaToActiveDevice(uri, context)
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                currentPositionMs = newPosition.positionMs
            }

            override fun onTracksChanged(tracks: Tracks) {
                super.onTracksChanged(tracks)
                val currentVideo = viewModel.currentVideo.value
                if (currentVideo != null && currentVideo.id != 0L) return

                val updatedVideo = player.remoteVideoInfo(currentVideo)
                viewModel.updateCurrentVideoInfo(updatedVideo)
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    LaunchedEffect(isDlnaMenuExpanded) {
        if (isDlnaMenuExpanded) {
            controlsVisibilityState.keepVisible()
        } else {
            controlsVisibilityState.releaseKeepVisible()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.castingEvent.collect { event ->
            when (event) {
                is CastingEvent.PlayNext -> {
                    if (player.hasNextMediaItem()) {
                        player.seekToNext()
                    } else {
                        viewModel.stopCasting(context)
                    }
                }
                is CastingEvent.PlayPrevious -> {
                    if (player.hasPreviousMediaItem()) {
                        player.seekToPrevious()
                    } else {
                        viewModel.stopCasting(context)
                    }
                }
                is CastingEvent.StopCasting -> {
                    viewModel.stopCasting(context)
                }
            }
        }
    }

    DisposableEffect(abRepeatA, abRepeatB) {
        val isSetAOnly = abRepeatA != C.TIME_UNSET && abRepeatB == C.TIME_UNSET
        if (isSetAOnly) controlsVisibilityState.keepVisible()
        onDispose {
            if (isSetAOnly) controlsVisibilityState.releaseKeepVisible()
        }
    }

    CompositionLocalProvider(LocalControlsVisibilityState provides controlsVisibilityState) {
        var interactionTick by remember { mutableIntStateOf(0) }
        val autoHideTimeout = playerPreferences.controllerAutoHideTimeout.seconds
        var wasCasting by remember { mutableStateOf(false) }
        var showBuffering by remember { mutableStateOf(false) }

        LaunchedEffect(dlnaPlaybackState.isActive) {
            if (dlnaPlaybackState.isActive) {
                wasCasting = true
                player.pause()
            } else if (wasCasting) {
                wasCasting = false
                controlsVisibilityState.showControls()
            }
        }

        LaunchedEffect(mediaPresentationState.isBuffering) {
            if (mediaPresentationState.isBuffering) {
                delay(250)
                showBuffering = true
            } else {
                showBuffering = false
            }
        }

        Box {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(Color.Black),
            ) {
                PlayerContentFrame(
                    player = player,
                    pictureInPictureState = pictureInPictureState,
                    controlsVisibilityState = controlsVisibilityState,
                    tapGestureState = tapGestureState,
                    seekGestureState = seekGestureState,
                    videoZoomAndContentScaleState = videoZoomAndContentScaleState,
                    volumeAndBrightnessGestureState = volumeAndBrightnessGestureState,
                    subtitleConfiguration = SubtitleConfiguration(
                        useSystemCaptionStyle = playerPreferences.useSystemCaptionStyle,
                        showBackground = playerPreferences.subtitleBackground,
                        font = playerPreferences.subtitleFont,
                        edgeType = playerPreferences.subtitleEdgeType,
                        textSize = playerPreferences.subtitleTextSize,
                        textBold = playerPreferences.subtitleTextBold,
                        applyEmbeddedStyles = playerPreferences.applyEmbeddedStyles,
                        subtitlePosition = playerPreferences.subtitlePosition,
                    ),
                    onTap = {
                        if (showAbRepeatPanel) {
                            showAbRepeatPanel = false
                            controlsVisibilityState.releaseKeepVisible()
                        } else {
                            controlsVisibilityState.toggleControlsVisibility()
                        }
                    }
                )

                AnimatedVisibility(
                    visible = controlsVisibilityState.controlsVisible && !controlsVisibilityState.controlsLocked,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    Box(
                        modifier = modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.3f)),
                    )
                }

                //if (mediaPresentationState.isBuffering) {
                if (showBuffering) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(72.dp),
                    )
                }

                DoubleTapIndicator(tapGestureState = tapGestureState)

                AnimatedVisibility(
                    modifier = Modifier
                        .padding(top = 24.dp)
                        .align(Alignment.TopCenter),
                    visible = tapGestureState.isLongPressGestureInAction,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    Surface(shape = CircleShape) {
                        Row(
                            modifier = Modifier.padding(
                                horizontal = 16.dp,
                                vertical = 8.dp,
                            ),
                        ) {
                            Text(
                                text = stringResource(coreUiR.string.fast_playback_speed, tapGestureState.longPressSpeed),
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                }

/*
                if (controlsVisibilityState.controlsVisible && controlsVisibilityState.controlsLocked) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .safeDrawingPadding()
                            .padding(top = 24.dp),
                    ) {
                        PlayerButton(
                            containerColor = Color.Black.copy(0.5f),
                            onClick = { controlsVisibilityState.unlockControls() }
                        ) {
                            Icon(
                                painter = painterResource(coreUiR.drawable.ic_lock),
                                contentDescription = stringResource(coreUiR.string.controls_unlock),
                            )
                        }
                    }
                } else {
                    PlayerControlsView(
                        topView = {
                            AnimatedVisibility(
                                visible = controlsVisibilityState.controlsVisible,
                                enter = fadeIn(),
                                exit = fadeOut(),
                            ) {
                                ControlsTopView(
                                    title = metadataState.title ?: "",
                                    onTitleClick = {
                                        controlsVisibilityState.hideControls()
                                        showVideoInfoDialog = true
                                    },
                                    onAudioClick = {
                                        controlsVisibilityState.hideControls()
                                        overlayView = OverlayView.AUDIO_SELECTOR
                                    },
                                    onSubtitleClick = {
                                        controlsVisibilityState.hideControls()
                                        overlayView = OverlayView.SUBTITLE_SELECTOR
                                    },
                                    onPlaybackSpeedClick = {
                                        controlsVisibilityState.hideControls()
                                        overlayView = OverlayView.PLAYBACK_SPEED
                                    },
                                    onPlaylistClick = {
                                        controlsVisibilityState.hideControls()
                                        overlayView = OverlayView.PLAYLIST
                                    },
                                    onBackClick = onBackClick,
                                )
                            }
                        },
                        middleView = {
                            when {
                                seekGestureState.seekAmount != null -> InfoView(info = "${seekGestureState.seekAmountFormatted}\n[${seekGestureState.seekToPositionFormated}]")
                                videoZoomAndContentScaleState.isZooming -> InfoView(info = "${(videoZoomAndContentScaleState.zoom * 100).toInt()}%")
                                videoZoomAndContentScaleState.showResetIndicator -> InfoView(info = "Reset")
                                videoZoomAndContentScaleState.showContentScaleIndicator -> InfoView(info = stringResource(videoZoomAndContentScaleState.videoContentScale.nameRes()))
                                controlsVisibilityState.controlsVisible -> ControlsMiddleView(player = player)
                                else -> Unit
                            }
                        },
                        bottomView = {
                            AnimatedVisibility(
                                visible = controlsVisibilityState.controlsVisible && !controlsVisibilityState.controlsLocked,
                                enter = fadeIn(),
                                exit = fadeOut(),
                            ) {
                                val context = LocalContext.current
                                ControlsBottomView(
                                    player = player,
                                    mediaPresentationState = mediaPresentationState,
                                    controlsAlignment = when (playerPreferences.controlButtonsPosition) {
                                        ControlButtonsPosition.LEFT -> Alignment.Start
                                        ControlButtonsPosition.RIGHT -> Alignment.End
                                    },
                                    videoContentScale = videoZoomAndContentScaleState.videoContentScale,
                                    isPipSupported = pictureInPictureState.isPipSupported,
                                    onSeek = seekGestureState::onSeek,
                                    onSeekEnd = seekGestureState::onSeekEnd,
                                    //onRotateClick = rotationState::rotate,
                                    onRotateClick = {
                                        controlsVisibilityState.showControls()
                                        rotationState.rotate()
                                    },
                                    onPlayInBackgroundClick = onPlayInBackgroundClick,
                                    onLockControlsClick = {
                                        controlsVisibilityState.showControls()
                                        controlsVisibilityState.lockControls()
                                    },
                                    onVideoContentScaleClick = {
                                        controlsVisibilityState.showControls()
                                        videoZoomAndContentScaleState.switchToNextVideoContentScale()
                                    },
                                    onVideoContentScaleLongClick = {
                                        //controlsVisibilityState.hideControls()
                                        //overlayView = OverlayView.VIDEO_CONTENT_SCALE
                                        controlsVisibilityState.showControls()
                                        videoZoomAndContentScaleState.resetZoomAndOffset()
                                    },
                                    onPictureInPictureClick = {
                                        if (!pictureInPictureState.hasPipPermission) {
                                            Toast.makeText(context, coreUiR.string.enable_pip_from_settings, Toast.LENGTH_SHORT).show()
                                            pictureInPictureState.openPictureInPictureSettings()
                                        } else {
                                            pictureInPictureState.enterPictureInPictureMode()
                                        }
                                    },
                                )
                            }
                        },
                    )
                }
*/

                when {
                    controlsVisibilityState.controlsVisible && controlsVisibilityState.controlsLocked -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .safeDrawingPadding()
                                .padding(top = 24.dp),
                        ) {
                            PlayerButton(
                                containerColor = Color.Black.copy(0.5f),
                                onClick = { controlsVisibilityState.unlockControls() },
                            ) {
                                Icon(
                                    painter = painterResource(coreUiR.drawable.ic_lock),
                                    contentDescription = stringResource(coreUiR.string.controls_unlock),
                                )
                            }
                        }
                    }

                    dlnaPlaybackState.isActive -> {
                        LaunchedEffect(controlsVisibilityState.controlsVisible, dlnaPlaybackState.isPlaying, interactionTick) {
                            if (controlsVisibilityState.controlsVisible && dlnaPlaybackState.isPlaying) {
                                delay(autoHideTimeout)
                                controlsVisibilityState.hideControls()
                            }
                        }

                        CastingControllerView(
                            mediaId = dlnaPlaybackState.mediaId,
                            position = dlnaPlaybackState.positionMs,
                            duration = dlnaPlaybackState.durationMs,
                            isPlaying = dlnaPlaybackState.isPlaying,
                            title = currentVideo?.nameWithExtension ?: metadataState.title ?: "",
                            controlsVisible = controlsVisibilityState.controlsVisible,
                            dlnaAutoplay = playerPreferences.dlnaAutoplay ?: false,
                            onToggleAutoplay = viewModel::toggleDlnaAutoplay,
                            onTap = {
                                if (controlsVisibilityState.controlsVisible) {
                                    controlsVisibilityState.hideControls()
                                } else {
                                    interactionTick++
                                    controlsVisibilityState.showControls()
                                }
                            },
                            onInteraction = {
                                interactionTick++
                                controlsVisibilityState.showControls()
                            },
                            onSeek = viewModel::seekCasting,
                            onStop = { viewModel.stopCasting(context) },
                            onPlayPause = viewModel::toggleCastingPlayPause,
                            onPrevious = viewModel::playPreviousCasting,
                            onNext = viewModel::playNextCasting,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    else -> {
                        PlayerControlsView(
                            topView = {
                                AnimatedVisibility(
                                    visible = controlsVisibilityState.controlsVisible,
                                    enter = fadeIn(),
                                    exit = fadeOut(),
                                ) {
                                    ControlsTopView(
                                        title = metadataState.title ?: "",
                                        onTitleClick = {
                                            controlsVisibilityState.hideControls()
                                            showVideoInfoDialog = true
                                        },
                                        onAudioClick = {
                                            controlsVisibilityState.hideControls()
                                            overlayView = OverlayView.AUDIO_SELECTOR
                                        },
                                        onSubtitleClick = {
                                            controlsVisibilityState.hideControls()
                                            overlayView = OverlayView.SUBTITLE_SELECTOR
                                        },
                                        onPlaybackSpeedClick = {
                                            controlsVisibilityState.hideControls()
                                            overlayView = OverlayView.PLAYBACK_SPEED
                                        },
                                        onPlaylistClick = {
                                            controlsVisibilityState.hideControls()
                                            overlayView = OverlayView.PLAYLIST
                                        },
                                        onBackClick = onBackClick,
                                    )
                                }
                            },
                            middleView = {
                                when {
                                    seekGestureState.seekAmount != null -> InfoView(info = "${seekGestureState.seekAmountFormatted}\n[${seekGestureState.seekToPositionFormated}]")
                                    videoZoomAndContentScaleState.isZooming -> InfoView(info = "${(videoZoomAndContentScaleState.zoom * 100).toInt()}%")
                                    videoZoomAndContentScaleState.showResetIndicator -> InfoView(info = "Reset")
                                    videoZoomAndContentScaleState.showContentScaleIndicator -> InfoView(info = stringResource(videoZoomAndContentScaleState.videoContentScale.nameRes()))
                                    controlsVisibilityState.controlsVisible -> ControlsMiddleView(player = player)
                                    else -> Unit
                                }
                            },
                            bottomView = {
                                AnimatedVisibility(
                                    visible = controlsVisibilityState.controlsVisible && !controlsVisibilityState.controlsLocked,
                                    enter = fadeIn(),
                                    exit = fadeOut(),
                                ) {
                                    val context = LocalContext.current

                                    val skipIntroTimeMs = playerPreferences.skipIntroTime * 1000L
                                    val durationMs = player.duration
                                    val isLongEnough = durationMs == C.TIME_UNSET || durationMs > skipIntroTimeMs

                                    val showSkipIntro = isLandscape && skipIntroTimeMs > 0 &&
                                            currentPositionMs < skipIntroTimeMs &&
                                            isLongEnough && !dlnaPlaybackState.isActive

                                    ControlsBottomView(
                                        player = player,
                                        mediaPresentationState = mediaPresentationState,
                                        controlsAlignment = when (playerPreferences.controlButtonsPosition) {
                                            ControlButtonsPosition.LEFT -> Alignment.Start
                                            ControlButtonsPosition.RIGHT -> Alignment.End
                                        },
                                        videoContentScale = videoZoomAndContentScaleState.videoContentScale,
                                        isPipSupported = pictureInPictureState.isPipSupported,
                                        onSeek = seekGestureState::onSeek,
                                        onSeekEnd = seekGestureState::onSeekEnd,
                                        //onRotateClick = rotationState::rotate,
                                        onRotateClick = {
                                            controlsVisibilityState.showControls()
                                            rotationState.rotate()
                                        },
                                        onPlayInBackgroundClick = onPlayInBackgroundClick,
                                        onLockControlsClick = {
                                            controlsVisibilityState.showControls()
                                            controlsVisibilityState.lockControls()
                                        },
                                        onVideoContentScaleClick = {
                                            controlsVisibilityState.showControls()
                                            videoZoomAndContentScaleState.switchToNextVideoContentScale()
                                        },
                                        onVideoContentScaleLongClick = {
                                            //controlsVisibilityState.hideControls()
                                            //overlayView = OverlayView.VIDEO_CONTENT_SCALE
                                            controlsVisibilityState.showControls()
                                            videoZoomAndContentScaleState.resetZoomAndOffset()
                                        },
                                        onPictureInPictureClick = {
                                            if (!pictureInPictureState.hasPipPermission) {
                                                Toast.makeText(context, coreUiR.string.enable_pip_from_settings, Toast.LENGTH_SHORT).show()
                                                pictureInPictureState.openPictureInPictureSettings()
                                            } else {
                                                coroutineScope.launch {
                                                    controlsVisibilityState.hideControls()
                                                    withFrameNanos {}
                                                    withFrameNanos {}
                                                    pictureInPictureState.enterPictureInPictureMode()
                                                }
                                            }
                                        },
                                        showSkipIntroButton = showSkipIntro,
                                        onSkipIntroClick = { player.seekTo(skipIntroTimeMs) },
                                        showBuffer = playerPreferences.showBuffer,
                                        abRepeatA = abRepeatA,
                                        abRepeatB = abRepeatB,
                                        onAbRepeatOnClick = {
                                            showAbRepeatPanel = !showAbRepeatPanel
                                            if (showAbRepeatPanel) {
                                                controlsVisibilityState.keepVisible()
                                            } else {
                                                controlsVisibilityState.releaseKeepVisible()
                                            }
                                        }
                                    )
                                }
                            },
                        )
                    }
                }

                if (showAbRepeatPanel && player != null) {
                    AbRepeatPanelOverlay(
                        player = player,
                        abRepeatA = abRepeatA,
                        abRepeatB = abRepeatB,
                        onStateChanged = { a, b ->
                            viewModel.abRepeatA = a
                            viewModel.abRepeatB = b
                        },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = if (isLandscape) 100.dp else 180.dp)
                    )
                }

                if (playerPreferences.dlnaCast && !dlnaPlaybackState.isActive) {
                    AnimatedVisibility(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .safeDrawingPadding()
                            .padding(top = 54.dp, end = 12.dp),
                        visible = controlsVisibilityState.controlsVisible &&
                                !controlsVisibilityState.controlsLocked,
                        enter = fadeIn(),
                        exit = fadeOut(),
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.5f),
                        ) {
                            DlnaCastButton(
                                devices = dlnaDevices,
                                isSearching = isDlnaSearching,
                                isCasting = dlnaPlaybackState.isActive,
                                expanded = isDlnaMenuExpanded,
                                onExpandedChange = { isDlnaMenuExpanded = it },
                                onOpen = { viewModel.searchDlnaDevices(context) },
                                onDeviceSelected = { device ->
                                    currentUri?.let { viewModel.castToDevice(device, it, context) }
                                    isDlnaMenuExpanded = false
                                },
                                onStopCasting = { viewModel.stopCasting(context) },
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }
                }

                val systemBarsPadding = WindowInsets.systemBars.asPaddingValues()
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .displayCutoutPadding()
                        .padding(systemBarsPadding.copy(top = 0.dp, bottom = 0.dp))
                        .padding(24.dp),
                ) {
                    AnimatedVisibility(
                        modifier = Modifier.align(Alignment.CenterStart),
                        visible = volumeAndBrightnessGestureState.activeGesture == VerticalGesture.VOLUME,
                        enter = fadeIn(),
                        exit = fadeOut(),
                    ) {
                        VerticalProgressView(
                            //value = volumeState.volumePercentage,
                            value = volumeState.currentVolume,
                            //maxValue = volumeState.maxVolumePercentage,
                            maxValue = volumeState.maxVolume,
                            icon = painterResource(coreUiR.drawable.ic_volume),
                        )
                    }

                    AnimatedVisibility(
                        modifier = Modifier.align(Alignment.CenterEnd),
                        visible = volumeAndBrightnessGestureState.activeGesture == VerticalGesture.BRIGHTNESS,
                        enter = fadeIn(),
                        exit = fadeOut(),
                    ) {
                        VerticalProgressView(
                            value = brightnessState.brightnessPercentage,
                            icon = painterResource(coreUiR.drawable.ic_brightness),
                        )
                    }
                }
            }

            OverlayShowView(
                player = player,
                overlayView = overlayView,
                videoContentScale = videoZoomAndContentScaleState.videoContentScale,
                isDrcEnabled = playerPreferences.enableDrc,
                onDrcToggle = { viewModel.toggleDrc() },
                drcPreset = playerPreferences.drcPreset,
                onDrcPresetChange = { viewModel.setDrcPreset(it) },
                onDismiss = { overlayView = null },
                onSelectSubtitleClick = onSelectSubtitleClick,
                onSubtitleOptionEvent = viewModel::onSubtitleOptionEvent,
                subtitleTextSize = playerPreferences.subtitleTextSize,
                initialPosition = playerPreferences.subtitlePosition,
                onVideoContentScaleChanged = { videoZoomAndContentScaleState.onVideoContentScaleChanged(it) },
            )

            if (showVideoInfoDialog) {
                currentVideo?.let { video ->
                    VideoInfoDialog(
                        video = video,
                        onDismiss = { showVideoInfoDialog = false }
                    )
                } ?: run {
                    showVideoInfoDialog = false
                }
            }
        }
    }

    errorState.error?.let { error ->
        AlertDialog(
            onDismissRequest = { },
            title = {
                Text(text = stringResource(coreUiR.string.error_playing_video))
            },
            text = {
                Text(text = error.message ?: stringResource(coreUiR.string.unknown_error))
            },
            confirmButton = {
                if (player.hasNextMediaItem()) {
                    TextButton(
                        onClick = {
                            errorState.dismiss()
                            player.seekToNext()
                            player.play()
                        },
                    ) {
                        Text(text = stringResource(coreUiR.string.play_next_video))
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        errorState.dismiss()
                        onBackClick()
                    },
                ) {
                    Text(text = stringResource(coreUiR.string.exit))
                }
            },
        )
    }

    BackHandler {
        if (overlayView != null) {
            overlayView = null
        } else {
            if (dlnaPlaybackState.isActive) {
                viewModel.stopCasting(context)
            }
            onBackClick()
        }
    }
}

@Composable
fun InfoView(
    modifier: Modifier = Modifier,
    info: String,
    textStyle: TextStyle = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = info,
            style = textStyle,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun ControlsMiddleView(modifier: Modifier = Modifier, player: Player) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(40.dp, alignment = Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PreviousButton(player = player)
        PlayPauseButton(player = player)
        NextButton(player = player)
    }
}

@Composable
fun PlayerControlsView(
    modifier: Modifier = Modifier,
    topView: @Composable () -> Unit,
    middleView: @Composable BoxScope.() -> Unit,
    bottomView: @Composable () -> Unit,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column {
            topView()
            Spacer(modifier = Modifier.weight(1f))
            bottomView()
        }

        middleView()
    }
}
