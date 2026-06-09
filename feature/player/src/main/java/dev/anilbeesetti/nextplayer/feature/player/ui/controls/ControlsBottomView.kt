package dev.anilbeesetti.nextplayer.feature.player.ui.controls

import androidx.annotation.OptIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import dev.anilbeesetti.nextplayer.core.model.VideoContentScale
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.designsystem.NextIcons
import dev.anilbeesetti.nextplayer.core.ui.extensions.copy
import dev.anilbeesetti.nextplayer.feature.player.LocalUseMaterialYouControls
import dev.anilbeesetti.nextplayer.feature.player.buttons.AbRepeatButton
import dev.anilbeesetti.nextplayer.feature.player.buttons.LoopButton
import dev.anilbeesetti.nextplayer.feature.player.buttons.PlayerButton
import dev.anilbeesetti.nextplayer.feature.player.buttons.ShuffleButton
import dev.anilbeesetti.nextplayer.feature.player.buttons.SkipIntroButton
import dev.anilbeesetti.nextplayer.feature.player.buttons.SleepTimerButton
import dev.anilbeesetti.nextplayer.feature.player.extensions.drawableRes
import dev.anilbeesetti.nextplayer.feature.player.extensions.noRippleClickable
import dev.anilbeesetti.nextplayer.feature.player.state.MediaPresentationState
import dev.anilbeesetti.nextplayer.feature.player.state.SleepTimerState
import dev.anilbeesetti.nextplayer.feature.player.state.durationFormatted
import dev.anilbeesetti.nextplayer.feature.player.state.pendingPositionFormatted
import dev.anilbeesetti.nextplayer.feature.player.state.positionFormatted

@OptIn(UnstableApi::class)
@Composable
fun ControlsBottomView(
    modifier: Modifier = Modifier,
    player: Player,
    mediaPresentationState: MediaPresentationState,
    controlsAlignment: Alignment.Horizontal,
    videoContentScale: VideoContentScale,
    isPipSupported: Boolean,
    onVideoContentScaleClick: () -> Unit,
    onVideoContentScaleLongClick: () -> Unit,
    onLockControlsClick: () -> Unit,
    onPictureInPictureClick: () -> Unit,
    onRotateClick: () -> Unit,
    onPlayInBackgroundClick: () -> Unit,
    showSkipIntroButton: Boolean = false,
    onSkipIntroClick: () -> Unit = {},
    abRepeatA: Long = C.TIME_UNSET,
    abRepeatB: Long = C.TIME_UNSET,
    onAbRepeatOnClick: () -> Unit,
    sleepTimerState: SleepTimerState,
    onSleepTimerClick: () -> Unit,
    onSeek: (Long) -> Unit,
    onSeekEnd: () -> Unit,
    showBuffer: Boolean = false,
) {
    val systemBarsPadding = WindowInsets.systemBars.union(WindowInsets.displayCutout).asPaddingValues()
    Column(
        modifier = modifier
            .padding(systemBarsPadding.copy(top = 0.dp))
            .padding(horizontal = 8.dp)
            .padding(top = 16.dp)
            .padding(bottom = 16.dp.takeIf { systemBarsPadding.calculateBottomPadding() == 0.dp } ?: 0.dp),
        //verticalArrangement = Arrangement.spacedBy(4.dp)
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            var showPendingPosition by rememberSaveable { mutableStateOf(false) }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.noRippleClickable {
                    showPendingPosition = !showPendingPosition
                },
            ) {
                Text(
                    text = when (showPendingPosition) {
                        true -> "-${mediaPresentationState.pendingPositionFormatted}"
                        false -> mediaPresentationState.positionFormatted
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                )
                Text(
                    text = " / ",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                )
                val bufferDiffSeconds = ((mediaPresentationState.bufferedPosition - mediaPresentationState.position) / 1000).coerceAtLeast(0L)
                Text(
                    //text = mediaPresentationState.durationFormatted,
                    text = buildAnnotatedString {
                        append(mediaPresentationState.durationFormatted)
                        if (showBuffer && bufferDiffSeconds > 0) {
                            withStyle(style = SpanStyle(color = Color.White.copy(alpha = 0.6f))) {
                                append(" (+${bufferDiffSeconds}s)")
                            }
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                )
                if (sleepTimerState.isActive) {
                    Spacer(modifier = Modifier.width(10.dp))

                    Row(
                        modifier = Modifier
                            .border(
                                width = 1.5.dp,
                                color = MaterialTheme.colorScheme.primary,
                                shape = CircleShape
                            )
                            .padding(start = 6.dp, top = 2.dp, end = 7.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = NextIcons.Snooze,
                            contentDescription = "SleepTimer",
                            modifier = Modifier.size(12.dp),
                            tint = Color.White
                        )

                        Spacer(modifier = Modifier.width(4.dp))

                        Text(
                            text = sleepTimerState.badgeText,
                            style = MaterialTheme.typography.labelMediumEmphasized,
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))
            PlayerButton(
                //modifier = modifier.size(30.dp),
                modifier = modifier.size(32.dp),
                onClick = onRotateClick,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_screen_rotation),
                    contentDescription = null,
                    //modifier = Modifier.size(20.dp),
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        PlayerSeekbar(
            position = mediaPresentationState.position.toFloat(),
            duration = mediaPresentationState.duration.toFloat(),
            bufferedPosition = if (showBuffer) mediaPresentationState.bufferedPosition.toFloat() else 0f,
            abRepeatA = abRepeatA,
            abRepeatB = abRepeatB,
            onSeek = { onSeek(it.toLong()) },
            onSeekFinished = { onSeekEnd() },
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    //.fillMaxWidth()
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp, alignment = controlsAlignment),
            ) {
                PlayerButton(onClick = onLockControlsClick) {
                    Icon(
                        painter = painterResource(R.drawable.ic_lock_open),
                        contentDescription = null,
                    )
                }
                PlayerButton(
                    onClick = onVideoContentScaleClick,
                    onLongClick = onVideoContentScaleLongClick,
                ) {
                    Icon(
                        painter = painterResource(videoContentScale.drawableRes()),
                        contentDescription = null,
                    )
                }
                LoopButton(player = player)
                ShuffleButton(player = player)
                if (isPipSupported) {
                    PlayerButton(onClick = onPictureInPictureClick) {
                        Icon(
                            painter = painterResource(R.drawable.ic_pip),
                            contentDescription = null,
                        )
                    }
                }
                PlayerButton(onClick = onPlayInBackgroundClick) {
                    Icon(
                        painter = painterResource(R.drawable.ic_headset),
                        contentDescription = null,
                    )
                }
                AbRepeatButton(
                    abRepeatA = abRepeatA,
                    abRepeatB = abRepeatB,
                    onClick = onAbRepeatOnClick,
                )
                SleepTimerButton(
                    isActive = sleepTimerState.formattedRemainingTime.isNotEmpty() || sleepTimerState.isPauseAtEndEnabled,
                    onClick = onSleepTimerClick
                )
            }

            if (showSkipIntroButton) {
                SkipIntroButton(
                    onClick = onSkipIntroClick,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
    }
}

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
//private fun PlayerSeekbar(
internal fun PlayerSeekbar(
    modifier: Modifier = Modifier,
    position: Float,
    duration: Float,
    bufferedPosition: Float = 0f,
    abRepeatA: Long = C.TIME_UNSET,
    abRepeatB: Long = C.TIME_UNSET,
    onSeek: (Float) -> Unit,
    onSeekFinished: () -> Unit,
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        if (LocalUseMaterialYouControls.current) {
            MaterialYouSlider(
                modifier = modifier.fillMaxWidth(),
                value = position,
                valueRange = 0f..duration,
                bufferedPosition = bufferedPosition,
                abRepeatA = abRepeatA,
                abRepeatB = abRepeatB,
                onValueChange = onSeek,
                onValueChangeFinished = onSeekFinished,
            )
        } else {
            SimpleSlider(
                modifier = modifier.fillMaxWidth(),
                value = position,
                valueRange = 0f..duration,
                bufferedPosition = bufferedPosition,
                abRepeatA = abRepeatA,
                abRepeatB = abRepeatB,
                onValueChange = onSeek,
                onValueChangeFinished = onSeekFinished,
            )
        }
    }
}

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MaterialYouSlider(
    modifier: Modifier = Modifier,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    bufferedPosition: Float = 0f,
    abRepeatA: Long = C.TIME_UNSET,
    abRepeatB: Long = C.TIME_UNSET,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val interactionSource = remember { MutableInteractionSource() }
    val trackHeight = 8.dp
    val thumbWidth = 4.dp
    val trackThumbGapWidth = 12.dp

    Slider(
        value = value,
        valueRange = valueRange,
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        interactionSource = interactionSource,
        modifier = modifier.size(24.dp),
        track = { sliderState ->
            val disabledAlpha = 0.4f

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(trackHeight),
            ) {
                val min = sliderState.valueRange.start
                val max = sliderState.valueRange.endInclusive
                val range = (max - min).takeIf { it > 0f } ?: 1f
                val playedFraction = ((sliderState.value - min) / range).coerceIn(0f, 1f)
                val playedPixels = size.width * playedFraction
                val bufferedFraction = ((bufferedPosition - min) / range).coerceIn(0f, 1f)
                val bufferedPixels = size.width * bufferedFraction

                val endCornerRadius = size.height / 2f
                val insideCornerRadius = 2.dp.toPx()
                val gapHalf = trackThumbGapWidth.toPx() / 2f
                val leftEnd = (playedPixels - gapHalf).coerceIn(0f, size.width)
                val rightStart = (playedPixels + gapHalf).coerceIn(0f, size.width)
/*
                // Inactive track left side
                if (leftEnd > 0f) {
                    drawRoundedRect(
                        offset = Offset(0f, 0f),
                        size = Size(leftEnd, size.height),
                        color = primaryColor.copy(alpha = disabledAlpha),
                        startCornerRadius = endCornerRadius,
                        endCornerRadius = insideCornerRadius,
                    )
                }

                // Inactive track right side
                if (rightStart < size.width) {
                    drawRoundedRect(
                        offset = Offset(rightStart, 0f),
                        size = Size(size.width - rightStart, size.height),
                        color = primaryColor.copy(alpha = disabledAlpha),
                        startCornerRadius = insideCornerRadius,
                        endCornerRadius = endCornerRadius,
                    )
                }

                // Active track
                if (leftEnd > 0f) {
                    drawRoundedRect(
                        offset = Offset(0f, 0f),
                        size = Size(leftEnd, size.height),
                        color = primaryColor,
                        startCornerRadius = endCornerRadius,
                        endCornerRadius = insideCornerRadius,
                    )
                }
*/
                if (leftEnd > 0f) {
                    drawRoundedRect(
                        offset = Offset(0f, 0f),
                        size = Size(leftEnd, size.height),
                        color = primaryColor,
                        startCornerRadius = endCornerRadius,
                        endCornerRadius = insideCornerRadius,
                    )
                }

                if (rightStart < size.width) {
                    drawRoundedRect(
                        offset = Offset(rightStart, 0f),
                        size = Size(size.width - rightStart, size.height),
                        color = primaryColor.copy(alpha = 0.3f),
                        startCornerRadius = insideCornerRadius,
                        endCornerRadius = endCornerRadius,
                    )
                }

                if (bufferedPixels > rightStart) {
                    val isBufferAtEnd = bufferedPixels >= size.width - 1f

                    drawRoundedRect(
                        offset = Offset(rightStart, 0f),
                        size = Size(bufferedPixels - rightStart, size.height),
                        color = primaryColor.copy(alpha = 0.4f),
                        startCornerRadius = insideCornerRadius,
                        endCornerRadius = if (isBufferAtEnd) endCornerRadius else insideCornerRadius,
                    )
                }

                if (abRepeatA != C.TIME_UNSET && abRepeatB != C.TIME_UNSET) {
                    val startX = size.width * ((abRepeatA.toFloat() - min) / range).coerceIn(0f, 1f)
                    val endX = size.width * ((abRepeatB.toFloat() - min) / range).coerceIn(0f, 1f)

                    drawRect(
                        color = Color.Yellow.copy(0.8f),
                        topLeft = Offset(startX, 0f),
                        size = Size(endX - startX, size.height)
                    )
                }

                fun drawMarker(timeMs: Long, color: Color) {
                    if (timeMs == C.TIME_UNSET) return
                    val fraction = ((timeMs.toFloat() - min) / range).coerceIn(0f, 1f)
                    val x = size.width * fraction

                    drawLine(
                        color = color,
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = 4.dp.toPx()
                    )
                }

                drawMarker(abRepeatA, Color.Yellow)
                drawMarker(abRepeatB, Color.Yellow)
            }
        },
        thumb = {
            Box(
                modifier = Modifier
                    .width(thumbWidth)
                    .height(20.dp)
                    //.background(primaryColor, CircleShape),
                    .background(
                        color = if (abRepeatA != C.TIME_UNSET) primaryColor.copy(alpha = 0.7f) else primaryColor,
                        shape = CircleShape
                    ),
            )
        },
    )
}

private fun DrawScope.drawRoundedRect(
    offset: Offset,
    size: Size,
    color: Color,
    startCornerRadius: Float,
    endCornerRadius: Float,
) {
    val startCorner = CornerRadius(startCornerRadius, startCornerRadius)
    val endCorner = CornerRadius(endCornerRadius, endCornerRadius)
    val track = RoundRect(
        rect = Rect(Offset(offset.x, 0f), size = Size(size.width, size.height)),
        topLeft = startCorner,
        topRight = endCorner,
        bottomRight = endCorner,
        bottomLeft = startCorner,
    )
    drawPath(
        path = Path().apply {
            addRoundRect(track)
        },
        color = color,
    )
}

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SimpleSlider(
    modifier: Modifier = Modifier,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    bufferedPosition: Float = 0f,
    abRepeatA: Long = C.TIME_UNSET,
    abRepeatB: Long = C.TIME_UNSET,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit
) {
    val primaryColor = MaterialTheme.colorScheme.primary

    Slider(
        value = value,
        valueRange = valueRange,
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        modifier = modifier.height(20.dp),
        thumb = {
            Box(
                modifier = Modifier.size(16.dp)
                    .shadow(4.dp, CircleShape)
                    //.background(Color.White)
                    .background(color = if (abRepeatA != C.TIME_UNSET) Color.White.copy(alpha = 0.7f) else Color.White)
            )
        },
        track = {
/*
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(Color.White.copy(0.5f))
            ) {

                if (valueRange.endInclusive > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(value / valueRange.endInclusive)
                            .height(4.dp)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
*/
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(MaterialTheme.shapes.extraSmall)
            ) {
                val range = valueRange.endInclusive.takeIf { it > 0f } ?: 1f
                val playedFraction = (value / range).coerceIn(0f, 1f)
                val playedPixels = size.width * playedFraction
                val bufferedFraction = (bufferedPosition / range).coerceIn(0f, 1f)
                val bufferedPixels = size.width * bufferedFraction

                drawRect(color = Color.White.copy(0.3f), size = size)

                if (bufferedPixels > playedPixels) {
                    drawRect(
                        color = Color.White.copy(0.6f),
                        topLeft = Offset(playedPixels, 0f),
                        size = Size(bufferedPixels - playedPixels, size.height)
                    )
                }

                if (playedPixels > 0f) {
                    drawRect(
                        color = primaryColor,
                        topLeft = Offset(0f, 0f),
                        size = Size(playedPixels, size.height)
                    )
                }

                if (abRepeatA != C.TIME_UNSET && abRepeatB != C.TIME_UNSET) {
                    val startX = size.width * (abRepeatA.toFloat() / range).coerceIn(0f, 1f)
                    val endX = size.width * (abRepeatB.toFloat() / range).coerceIn(0f, 1f)
                    drawRect(
                        color = Color.Yellow.copy(0.8f),
                        topLeft = Offset(startX, 0f),
                        size = Size(endX - startX, size.height)
                    )
                }

                fun drawMarker(timeMs: Long) {
                    if (timeMs == C.TIME_UNSET) return
                    val x = size.width * (timeMs.toFloat() / range).coerceIn(0f, 1f)
                    drawLine(
                        color = Color.Yellow,
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = 4.dp.toPx()
                    )
                }

                drawMarker(abRepeatA)
                drawMarker(abRepeatB)
            }
        }
    )
}
