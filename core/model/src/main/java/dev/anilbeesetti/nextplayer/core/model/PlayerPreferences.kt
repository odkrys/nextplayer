package dev.anilbeesetti.nextplayer.core.model

import kotlinx.serialization.Serializable

@Serializable
data class PlayerPreferences(
    val resume: Resume = Resume.NO,
    val rememberPlayerBrightness: Boolean = true,
    val playerBrightness: Float = 0.5f,
    val fastSeek: FastSeek = FastSeek.AUTO,
    val minDurationForFastSeek: Long = 120000L,
    val rememberSelections: Boolean = true,
    val playerScreenOrientation: ScreenOrientation = ScreenOrientation.VIDEO_ORIENTATION,
    val controlButtonsPosition: ControlButtonsPosition = ControlButtonsPosition.LEFT,
    val playerVideoZoom: VideoZoom = VideoZoom.BEST_FIT,
    val defaultPlaybackSpeed: Float = 1.0f,
    val controllerAutoHideTimeout: Int = 2,
    val seekIncrement: Int = 5,
    val autoplay: Boolean = true,
    val videoLoop: VideoLoop = VideoLoop.LOOP_OFF,
    val autoPip: Boolean = false,
    val autoBackgroundPlay: Boolean = false,
    val isShuffleOn: Shuffle = Shuffle.SHUFFLE_OFF,

    // Controls (Gestures)
    val useSwipeControls: Boolean = true,
    val useSeekControls: Boolean = true,
    val useZoomControls: Boolean = true,
    val doubleTapGesture: DoubleTapGesture = DoubleTapGesture.FAST_FORWARD_AND_REWIND,
    val useLongPressControls: Boolean = true,
    val longPressControlsSpeed: Float = 2.0f,

    // Audio Preferences
    val preferredAudioLanguage: String = "",
    val pauseOnHeadsetDisconnect: Boolean = true,
    val requireAudioFocus: Boolean = true,
    val showSystemVolumePanel: Boolean = true,
    val shouldUseVolumeBoost: Boolean = false,

    // Subtitle Preferences
    val useSystemCaptionStyle: Boolean = false,
    val preferredSubtitleLanguage: String = "",
    val subtitleTextEncoding: String = "",
    val subtitleTextSize: Int = 20,
    val subtitleBackground: Boolean = false,
    val subtitleFont: Font = Font.DEFAULT,
    val subtitleTextBold: Boolean = true,
    val applyEmbeddedStyles: Boolean = true,

    // Decoder Preferences
    val decoderPriority: DecoderPriority = DecoderPriority.PREFER_DEVICE,
)
