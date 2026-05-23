package dev.anilbeesetti.nextplayer.feature.player.ui

import androidx.annotation.OptIn
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.components.PreferenceSlider
import dev.anilbeesetti.nextplayer.core.ui.designsystem.NextIcons
import dev.anilbeesetti.nextplayer.feature.player.extensions.getName
import dev.anilbeesetti.nextplayer.feature.player.state.SubtitleOptionsEvent
import dev.anilbeesetti.nextplayer.feature.player.state.rememberSubtitleOptionsState
import dev.anilbeesetti.nextplayer.feature.player.state.rememberTracksState
import kotlin.math.roundToLong
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
@Composable
fun BoxScope.SubtitleSelectorView(
    modifier: Modifier = Modifier,
    show: Boolean,
    player: Player,
    onSelectSubtitleClick: () -> Unit,
    subtitleTextSize: Int,
    initialPosition: Float,
    onEvent: (SubtitleOptionsEvent) -> Unit = {},
    onDismiss: () -> Unit,
) {
    val subtitleTracksState = rememberTracksState(player, C.TRACK_TYPE_TEXT)
    //val subtitleOptionsState = rememberSubtitleOptionsState(player, onEvent)
    val subtitleOptionsState = rememberSubtitleOptionsState(player, initialPosition, onEvent)
    var showSettings by remember { mutableStateOf(false) }
    var currentAlpha by remember { mutableFloatStateOf(1f) }

    OverlayView(
        //modifier = modifier,
        modifier = modifier
            .graphicsLayer(alpha = currentAlpha),
        show = show,
        title = stringResource(R.string.select_subtitle_track),
    ) {
        Column(
            modifier = Modifier
                //.verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
                .padding(horizontal = 24.dp)
                //.selectableGroup(),
        ) {
/*
            subtitleTracksState.tracks.forEachIndexed { index, track ->
                RadioButtonRow(
                    selected = track.isSelected,
                    text = track.mediaTrackGroup.getName(C.TRACK_TYPE_TEXT, index),
                    onClick = {
                        subtitleTracksState.switchTrack(index)
                        onDismiss()
                    },
                )
            }
            RadioButtonRow(
                selected = subtitleTracksState.tracks.none { it.isSelected },
                text = stringResource(R.string.disable),
                onClick = {
                    subtitleTracksState.switchTrack(-1)
                    onDismiss()
                },
            )
            Spacer(modifier = Modifier.size(16.dp))
 */
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
            ) {
                subtitleTracksState.tracks.forEachIndexed { index, track ->
                    val baseName = track.mediaTrackGroup.getName(C.TRACK_TYPE_TEXT, index)
                    val format = track.mediaTrackGroup.getFormat(0)

                    val extension = remember(format) {
                        val sourceString = format.label ?: format.id ?: ""
                        val ext = sourceString.substringAfterLast('.', "").uppercase()

                        if (ext.isNotEmpty() && ext.length <= 4 && ext.all { it.isLetter() }) {
                            ext
                        } else {
                            val mime = format.sampleMimeType ?: format.containerMimeType ?: ""
                            val codecs = format.codecs?.lowercase() ?: ""

                            when {
                                mime == androidx.media3.common.MimeTypes.TEXT_SSA || codecs.contains("ssa") || codecs.contains("ass") -> "SSA"
                                mime == androidx.media3.common.MimeTypes.TEXT_VTT || codecs.contains("vtt") -> "VTT"
                                mime == androidx.media3.common.MimeTypes.APPLICATION_TTML || codecs.contains("ttml") || codecs.contains("xml") || codecs.contains("dfxp") -> "TTML"
                                mime == androidx.media3.common.MimeTypes.APPLICATION_SUBRIP || codecs.contains("subrip") || codecs.contains("srt") -> "SRT"
                                else -> ""
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            RadioButtonRow(
                                selected = track.isSelected,
                                text = baseName,
                                onClick = {
                                    subtitleTracksState.switchTrack(index)
                                    onDismiss()
                                },
                            )
                        }

                        if (extension.isNotEmpty()) {
                            Surface(
                                modifier = Modifier
                                    .padding(end = 12.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = extension,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.size(8.dp))

            if (!showSettings) {
                RadioButtonRow(
                    selected = subtitleTracksState.tracks.none { it.isSelected },
                    text = stringResource(R.string.disable),
                    onClick = {
                        subtitleTracksState.switchTrack(-1)
                        onDismiss()
                    },
                )
            }
/*
            FilledTonalButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    onSelectSubtitleClick()
                    onDismiss()
                },
            ) {
                Text(text = stringResource(R.string.open_subtitle))
            }
            Spacer(modifier = Modifier.size(16.dp))
            DelayInput(
                value = subtitleOptionsState.delayMilliseconds,
                onValueChange = { subtitleOptionsState.setDelay(it) },
            )
            Spacer(modifier = Modifier.size(16.dp))
            SpeedInput(
                value = subtitleOptionsState.speedMultiplier,
                onValueChange = { subtitleOptionsState.setSpeed(it) },
            )

*/
            Spacer(modifier = Modifier.size(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalButton(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onSelectSubtitleClick()
                        onDismiss()
                    },
                ) {
                    Text(text = stringResource(R.string.open_subtitle))
                }

                FilledTonalIconButton(
                    onClick = { showSettings = !showSettings },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = NextIcons.Settings,
                        contentDescription = "Subtitle Settings"
                    )
                }
            }

            if (showSettings) {
                //Column(modifier = Modifier.padding(top = 16.dp)) {
                Column(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .heightIn(max = 250.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 8.dp)
                ) {
                    PreferenceSlider(
                        title = "Text Size: $subtitleTextSize",
                        icon = NextIcons.FontSize,
                        value = subtitleTextSize.toFloat(),
                        valueRange = 10f..60f,
                        onValueChange = { newValue ->
                            currentAlpha = 0.4f
                            onEvent(SubtitleOptionsEvent.UpdateSubtitleTextSize(newValue.toInt()))
                        },
                        onValueChangeFinished = {
                            currentAlpha = 1f
                        },
                        trailingContent = {
                            FilledIconButton(
                                onClick = { onEvent(SubtitleOptionsEvent.UpdateSubtitleTextSize(20)) }
                            ) {
                                Icon(imageVector = NextIcons.History, contentDescription = "Reset")
                            }
                        },
                    )

                    PreferenceSlider(
                        title = "Position: ${(subtitleOptionsState.positionRatio * 100).toInt()}",
                        icon = NextIcons.SubtitlePosition,
                        value = subtitleOptionsState.positionRatio,
                        valueRange = 0f..0.95f,
                        onValueChange = { newValue ->
                            currentAlpha = 0.4f
                            subtitleOptionsState.setPosition(newValue)
                        },
                        onValueChangeFinished = {
                            currentAlpha = 1f
                        },
                        trailingContent = {
                            FilledIconButton(
                                onClick = { subtitleOptionsState.setPosition(0.08f) }
                            ) {
                                Icon(imageVector = NextIcons.History, contentDescription = "Reset")
                            }
                        },
                    )

                    Spacer(modifier = Modifier.size(12.dp))

                    DelayInput(
                        value = subtitleOptionsState.delayMilliseconds,
                        onValueChange = { subtitleOptionsState.setDelay(it) },
                    )

                    Spacer(modifier = Modifier.size(12.dp))

                    SpeedInput(
                        value = subtitleOptionsState.speedMultiplier,
                        onValueChange = { subtitleOptionsState.setSpeed(it) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DelayInput(
    value: Long,
    onValueChange: (Long) -> Unit,
) {
    var valueString by remember {
        mutableStateOf(if (value == 0L) "0" else "%.2f".format(value / 1000.0))
    }

    LaunchedEffect(value) {
        val currentValue = valueString.toDoubleOrNull() ?: 0.0
        if (currentValue == (value / 1000.0)) return@LaunchedEffect
        valueString = if (value == 0L) "0" else "%.2f".format(value / 1000.0)
    }

    NumberChooserInput(
        title = stringResource(R.string.delay),
        value = valueString,
        suffix = { Text(text = "sec") },
        onValueChange = { newValue ->
            if (newValue.isBlank()) {
                valueString = ""
                onValueChange(0)
                return@NumberChooserInput
            }

            val cleanedValue = newValue.trimStart()

            if (cleanedValue == "-" || cleanedValue == ".") {
                valueString = cleanedValue
                return@NumberChooserInput
            }

            val decimalPattern = "^-?\\d*\\.?\\d{0,2}$".toRegex()
            if (!cleanedValue.matches(decimalPattern)) {
                return@NumberChooserInput
            }

            valueString = cleanedValue

            runCatching {
                val doubleValue = cleanedValue.toDoubleOrNull() ?: 0.0
                val milliseconds = (doubleValue * 1000).roundToLong()
                onValueChange(milliseconds)
            }
        },
        onIncrement = { onValueChange(value + 100) },
        onDecrement = { onValueChange(value - 100) },
    )
}

@Composable
private fun SpeedInput(
    value: Float,
    onValueChange: (Float) -> Unit,
) {
    var valueString by remember {
        mutableStateOf(if (value == 1f) "1" else "%.2f".format(value))
    }

    LaunchedEffect(value) {
        val currentValue = valueString.toFloatOrNull() ?: 0.0
        if (currentValue == value) return@LaunchedEffect
        valueString = if (value == 1f) "1" else "%.2f".format(value)
    }

    NumberChooserInput(
        title = stringResource(R.string.speed),
        value = valueString,
        suffix = { Text(text = "x") },
        onValueChange = { newValue ->
            if (newValue.isBlank()) {
                valueString = ""
                onValueChange(1f)
                return@NumberChooserInput
            }

            val cleanedValue = newValue.trimStart()

            if (cleanedValue == ".") {
                valueString = cleanedValue
                return@NumberChooserInput
            }

            val decimalPattern = "^\\d*\\.?\\d{0,2}$".toRegex()
            if (!cleanedValue.matches(decimalPattern)) {
                return@NumberChooserInput
            }

            valueString = cleanedValue

            runCatching {
                val floatValue = cleanedValue.toFloatOrNull() ?: 1f
                onValueChange(floatValue)
            }
        },
        onIncrement = { onValueChange(value + 0.1f) },
        onDecrement = { onValueChange(value - 0.1f) },
    )
}

@Composable
private fun NumberChooserInput(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    onIncrement: () -> Unit = {},
    onDecrement: () -> Unit = {},
    suffix: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        FilledTonalIconButton(
            onClick = { },
            modifier = Modifier.repeatingClickable(onClick = onDecrement),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_remove),
                contentDescription = null,
            )
        }
        OutlinedTextField(
            label = { Text(text = title) },
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            suffix = suffix,
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
            ),
        )
        FilledTonalIconButton(
            onClick = { },
            modifier = Modifier.repeatingClickable(onClick = onIncrement),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_add),
                contentDescription = null,
            )
        }
    }
}

private fun Modifier.repeatingClickable(
    enabled: Boolean = true,
    maxDelayMillis: Long = 200,
    minDelayMillis: Long = 5,
    delayDecayFactor: Float = .20f,
    onClick: () -> Unit,
): Modifier = composed {
    val updatedOnClick by rememberUpdatedState(onClick)

    this.pointerInput(enabled) {
        coroutineScope {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val heldButtonJob = launch {
                    var currentDelayMillis = maxDelayMillis
                    while (enabled && down.pressed) {
                        updatedOnClick()
                        delay(currentDelayMillis)
                        val nextMillis = currentDelayMillis - (currentDelayMillis * delayDecayFactor)
                        currentDelayMillis = nextMillis.toLong().coerceAtLeast(minDelayMillis)
                    }
                }
                waitForUpOrCancellation()
                heldButtonJob.cancel()
            }
        }
    }
}
