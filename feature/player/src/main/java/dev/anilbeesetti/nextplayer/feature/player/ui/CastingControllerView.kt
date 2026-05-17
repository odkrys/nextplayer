package dev.anilbeesetti.nextplayer.feature.player.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.anilbeesetti.nextplayer.core.ui.designsystem.NextIcons
import dev.anilbeesetti.nextplayer.core.ui.R as coreUiR
import dev.anilbeesetti.nextplayer.feature.player.buttons.PlayerButton
import dev.anilbeesetti.nextplayer.feature.player.ui.controls.PlayerSeekbar

@Composable
fun CastingControllerView(
    mediaId: String,
    position: Long,
    duration: Long,
    isPlaying: Boolean,
    title: String,
    controlsVisible: Boolean,
    dlnaAutoplay: Boolean,
    onToggleAutoplay: () -> Unit,
    onTap: () -> Unit,
    onInteraction: () -> Unit,
    onSeek: (Long) -> Unit,
    onStop: () -> Unit,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isSeeking by remember { mutableStateOf(false) }
    var seekPosition by remember { mutableFloatStateOf(0f) }
    var ignoreNetworkPosition by remember { mutableStateOf(false) }

    LaunchedEffect(mediaId) {
        isSeeking = false
        ignoreNetworkPosition = false
        seekPosition = 0f
    }

    LaunchedEffect(position) {
        if (ignoreNetworkPosition) {
            val diff = kotlin.math.abs(position - seekPosition.toLong())
            if (diff < 5000L) {
                ignoreNetworkPosition = false
            }
        }
    }

    LaunchedEffect(ignoreNetworkPosition) {
        if (ignoreNetworkPosition) {
            kotlinx.coroutines.delay(4000L)
            ignoreNetworkPosition = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .systemBarsPadding()
            .pointerInput(Unit) {
                detectTapGestures { onTap() }
            }
    ) {
        Icon(
            imageVector = NextIcons.Cast,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.25f),
            modifier = Modifier
                .align(Alignment.Center)
                .size(120.dp)
        )

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(horizontal = 32.dp, vertical = 16.dp)
                ) {
                    Text(
                        text = title,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(modifier = Modifier.align(Alignment.End)) {
                        PlayerButton(
                            onClick = { onInteraction(); onToggleAutoplay() }
                        ) {
                            Icon(
                                painter = painterResource(coreUiR.drawable.ic_dlna_autoplay),
                                contentDescription = "",
                                tint = if (dlnaAutoplay) MaterialTheme.colorScheme.primary else Color.White
                            )
                        }

                        PlayerButton(
                            onClick = { onInteraction(); onStop() }
                        ) {
                            Icon(
                                imageVector = NextIcons.CastConnected,
                                contentDescription = "",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(40.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PlayerButton(
                        onClick = { onInteraction(); onPrevious() },
                        modifier = Modifier.size(48.dp)) {
                        Icon(
                            painter = painterResource(coreUiR.drawable.ic_skip_prev),
                            contentDescription = "",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    PlayerButton(
                        onClick = { onInteraction(); onPlayPause() },
                        modifier = Modifier.size(64.dp)) {
                        Icon(
                            painter = painterResource(
                                if (isPlaying) coreUiR.drawable.ic_pause else coreUiR.drawable.ic_play
                            ),
                            contentDescription = "",
                            tint = Color.White,
                            modifier = Modifier.size(48.dp)
                        )
                    }

                    PlayerButton(
                        onClick = { onInteraction(); onNext() },
                        modifier = Modifier.size(48.dp)) {
                        Icon(
                            painter = painterResource(coreUiR.drawable.ic_skip_next),
                            contentDescription = "",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 32.dp, vertical = 60.dp)
                ) {
                    val displayPositionMs = when {
                        isSeeking || ignoreNetworkPosition -> seekPosition
                        else -> position.toFloat()
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = formatDuration(displayPositionMs.toLong()),
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge
                        )
                        Text(
                            formatDuration(duration),
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    PlayerSeekbar(
                        position = displayPositionMs,
                        duration = if (duration > 0) duration.toFloat() else 1.0f,
                        onSeek = { draggedMs ->
                            if (duration > 0L) {
                                onInteraction()
                                isSeeking = true
                                seekPosition = draggedMs
                            }
                        },
                        onSeekFinished = {
                            if (duration > 0L) {
                                isSeeking = false
                                ignoreNetworkPosition = true
                                onSeek(seekPosition.toLong())
                            }
                        }
                    )
                }
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val s = ms / 1000
    val m = s / 60
    val h = m / 60
    return if (h > 0) "%d:%02d:%02d".format(h, m % 60, s % 60)
    else "%02d:%02d".format(m, s % 60)
}
