package dev.anilbeesetti.nextplayer.feature.player.ui

import android.content.res.Configuration
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import dev.anilbeesetti.nextplayer.core.model.DrcPreset
import dev.anilbeesetti.nextplayer.core.model.PlayerPreferences
import dev.anilbeesetti.nextplayer.core.model.VideoContentScale
import dev.anilbeesetti.nextplayer.feature.player.extensions.noRippleClickable
import dev.anilbeesetti.nextplayer.feature.player.state.SleepTimerState
import dev.anilbeesetti.nextplayer.feature.player.state.SubtitleOptionsEvent

@OptIn(UnstableApi::class)
@Composable
fun BoxScope.OverlayShowView(
    player: Player,
    overlayView: OverlayView?,
    playerPreferences: PlayerPreferences,
    videoContentScale: VideoContentScale,
    isSkipSilenceEnabled: Boolean = false,
    onSkipSilenceToggle: (Boolean) -> Unit,
    isDrcSupported: Boolean = false,
    isDrcEnabled: Boolean = false,
    onDrcToggle: (Boolean) -> Unit = {},
    drcPreset: DrcPreset = DrcPreset.LIGHT,
    onDrcPresetChange: (DrcPreset) -> Unit = {},
    activeOutputChannels: Int = -1,
    isCenterBoostEnabled: Boolean = false,
    onCenterBoostToggle: (Boolean) -> Unit = {},
    centerBoostDb: Int = 0,
    onCenterBoostDbChange: (Int) -> Unit = {},
    sleepTimerState: SleepTimerState,
    subtitleTextSize: Int = 20,
    initialPosition: Float = 0.08f,
    onDismiss: () -> Unit = {},
    onSelectSubtitleClick: () -> Unit = {},
    onSubtitleOptionEvent: (SubtitleOptionsEvent) -> Unit = {},
    onVideoContentScaleChanged: (VideoContentScale) -> Unit = {},
) {
    Box(
        modifier = Modifier
            .matchParentSize()
            .then(
                if (overlayView != null) {
                    Modifier.noRippleClickable(onClick = onDismiss)
                } else {
                    Modifier
                },
            ),
    )

    AudioTrackSelectorView(
        show = overlayView == OverlayView.AUDIO_SELECTOR,
        player = player,
        isSkipSilenceEnabled = isSkipSilenceEnabled,
        onSkipSilenceToggle = onSkipSilenceToggle,
        isDrcSupported = isDrcSupported,
        isDrcEnabled = isDrcEnabled,
        onDrcToggle = onDrcToggle,
        drcPreset = drcPreset,
        onDrcPresetChange = onDrcPresetChange,
        activeOutputChannels = activeOutputChannels,
        isCenterBoostEnabled = isCenterBoostEnabled,
        onCenterBoostToggle = onCenterBoostToggle,
        centerBoostDb = centerBoostDb,
        onCenterBoostDbChange = onCenterBoostDbChange,
        onDismiss = onDismiss,
    )

    SubtitleSelectorView(
        show = overlayView == OverlayView.SUBTITLE_SELECTOR,
        player = player,
        onSelectSubtitleClick = onSelectSubtitleClick,
        subtitleTextSize = subtitleTextSize,
        initialPosition = initialPosition,
        onEvent = onSubtitleOptionEvent,
        onDismiss = onDismiss,
    )

    PlaybackSpeedSelectorView(
        show = overlayView == OverlayView.PLAYBACK_SPEED,
        player = player,
    )

    VideoContentScaleSelectorView(
        show = overlayView == OverlayView.VIDEO_CONTENT_SCALE,
        videoContentScale = videoContentScale,
        onVideoContentScaleChanged = onVideoContentScaleChanged,
        onDismiss = onDismiss,
    )

    PlaylistView(
        show = overlayView == OverlayView.PLAYLIST,
        player = player,
    )

    SleepTimerView(
        show = overlayView == OverlayView.SLEEP_TIMER,
        sleepTimerState = sleepTimerState,
        lastSleepTimerMinutes = playerPreferences.lastSleepTimerMinutes,
    )
}

val Configuration.isPortrait: Boolean
    get() = orientation == Configuration.ORIENTATION_PORTRAIT

enum class OverlayView {
    AUDIO_SELECTOR,
    SUBTITLE_SELECTOR,
    PLAYBACK_SPEED,
    VIDEO_CONTENT_SCALE,
    PLAYLIST,
    SLEEP_TIMER,
}
