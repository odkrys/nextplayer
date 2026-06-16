package dev.anilbeesetti.nextplayer.feature.player.ui

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import dev.anilbeesetti.nextplayer.core.model.DrcPreset
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.components.NextSwitch
import dev.anilbeesetti.nextplayer.feature.player.extensions.getName
import dev.anilbeesetti.nextplayer.feature.player.service.getActiveOutputChannels
import dev.anilbeesetti.nextplayer.feature.player.service.getIsDrcSupported
import dev.anilbeesetti.nextplayer.feature.player.service.setCenterBoostDb
import dev.anilbeesetti.nextplayer.feature.player.service.setDrcEnabled
import dev.anilbeesetti.nextplayer.feature.player.service.setDrcPreset
import dev.anilbeesetti.nextplayer.feature.player.service.setSkipSilenceEnabled
import dev.anilbeesetti.nextplayer.feature.player.state.rememberSkipSilenceState
import dev.anilbeesetti.nextplayer.feature.player.state.rememberTracksState
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@OptIn(UnstableApi::class)
@Composable
fun BoxScope.AudioTrackSelectorView(
    modifier: Modifier = Modifier,
    show: Boolean,
    player: Player,
    isSkipSilenceEnabled: Boolean,
    onSkipSilenceToggle: (Boolean) -> Unit,
    isDrcEnabled: Boolean,
    drcPreset: DrcPreset,
    onDrcToggle: (Boolean) -> Unit,
    onDrcPresetChange: (DrcPreset) -> Unit,
    isCenterBoostEnabled: Boolean,
    centerBoostDb: Int = 0,
    onCenterBoostToggle: (Boolean) -> Unit,
    onCenterBoostDbChange: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val audioTracksState = rememberTracksState(player, C.TRACK_TYPE_AUDIO)
    val skipSilenceState = rememberSkipSilenceState(player)
    var isDrcSupported by remember { mutableStateOf(false) }
    var currentBoostDb by remember { mutableIntStateOf(centerBoostDb) }
    var activeOutputChannels by remember { mutableIntStateOf(-1) }
    val isCenterBoostSupported = activeOutputChannels > 2

    LaunchedEffect(player, show, centerBoostDb) {
        if (show && player is MediaController) {
            isDrcSupported = player.getIsDrcSupported()
            currentBoostDb = centerBoostDb
            var channels = player.getActiveOutputChannels()
            var attempts = 0

            while (channels == -1 && attempts < 15) {
                delay(200)
                channels = player.getActiveOutputChannels()
                attempts++
            }

            activeOutputChannels = channels
        }
    }

    OverlayView(
        modifier = modifier,
        show = show,
        title = stringResource(R.string.select_audio_track),
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
                .padding(horizontal = 24.dp)
                .selectableGroup(),
        ) {
            audioTracksState.tracks.forEachIndexed { index, track ->
/*
                RadioButtonRow(
                    selected = track.isSelected,
                    text = track.mediaTrackGroup.getName(C.TRACK_TYPE_AUDIO, index),
                    onClick = {
                        audioTracksState.switchTrack(index)
                        onDismiss()
                    },
                )
*/
                val sourceChannels = track.takeIf { it.length > 0 }
                    ?.getTrackFormat(0)
                    ?.channelCount ?: 2

                val badgeText = if (track.isSelected) {
                    if (activeOutputChannels == -1) {
                        ""
                    } else if (activeOutputChannels == 2 && sourceChannels > 2) {
                        "2ch (Down)"
                    } else {
                        when (activeOutputChannels) {
                            1 -> "Mono"
                            2 -> "2ch"
                            6 -> "5.1ch"
                            8 -> "7.1ch"
                            in 3..100 -> "${activeOutputChannels}ch"
                            else -> ""
                        }
                    }
                } else {
                    when (sourceChannels) {
                        1 -> "Mono"
                        2 -> "2ch"
                        6 -> "5.1ch"
                        8 -> "7.1ch"
                        in 3..100 -> "${sourceChannels}ch"
                        else -> ""
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        RadioButtonRow(
                            selected = track.isSelected,
                            text = track.mediaTrackGroup.getName(C.TRACK_TYPE_AUDIO, index),
                            onClick = {
                                audioTracksState.switchTrack(index)
                                onDismiss()
                            },
                        )
                    }

                    if (badgeText.isNotEmpty()) {
                        androidx.compose.material3.Surface(
                            modifier = Modifier.padding(end = 12.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = badgeText,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                            )
                        }
                    }
                }
            }
            RadioButtonRow(
                selected = audioTracksState.tracks.none { it.isSelected },
                text = stringResource(R.string.disable),
                onClick = {
                    audioTracksState.switchTrack(-1)
                    onDismiss()
                },
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
            )

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .toggleable(
                        //value = skipSilenceState.skipSilenceEnabled,
                        //onValueChange = { skipSilenceState.setSkipSilence(it) },
                        value = isSkipSilenceEnabled,
                        onValueChange = { enabled ->
                            onSkipSilenceToggle(enabled)
                            if (player is MediaController) player.setSkipSilenceEnabled(enabled)
                        },
                    )
                    .fillMaxWidth()
                    .padding(8.dp)
                    .semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = stringResource(R.string.skip_silence),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                NextSwitch(
                    //checked = skipSilenceState.skipSilenceEnabled,
                    checked = isSkipSilenceEnabled,
                    onCheckedChange = null,
                )
            }

            when (isDrcSupported) {
                null -> {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(8.dp).alpha(0f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.dynamic_range_compressor),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        NextSwitch(checked = false, onCheckedChange = null)
                    }
                }
                true -> {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .toggleable(
                                value = isDrcEnabled,
                                onValueChange = { enabled ->
                                    onDrcToggle(enabled)
                                    if (player is MediaController) player.setDrcEnabled(enabled)
                                },
                            )
                            .fillMaxWidth()
                            .padding(8.dp)
                            .semantics(mergeDescendants = true) {},
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.dynamic_range_compressor),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        NextSwitch(checked = isDrcEnabled, onCheckedChange = null)
                    }
                    if (isDrcEnabled) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.drc_preset),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            listOf(DrcPreset.LIGHT, DrcPreset.STRONG).forEach { preset ->
                                val selected = drcPreset == preset
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(
                                            if (selected) MaterialTheme.colorScheme.primaryContainer
                                            else Color.Transparent
                                        )
                                        .border(
                                            width = 1.dp,
                                            color = if (selected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                            shape = RoundedCornerShape(6.dp),
                                        )
                                        .clickable {
                                            onDrcPresetChange(preset)
                                            if (player is MediaController) player.setDrcPreset(preset)
                                        }
                                        .padding(horizontal = 16.dp, vertical = 6.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = stringResource(
                                            when (preset) {
                                                DrcPreset.LIGHT -> R.string.drc_preset_light
                                                DrcPreset.STRONG -> R.string.drc_preset_strong
                                            }
                                        ),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (selected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                        }
                    }
                }
                false -> Unit
            }

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .toggleable(
                        value = isCenterBoostEnabled,
                        enabled = isCenterBoostSupported,
                        onValueChange = { enabled ->
                            onCenterBoostToggle(enabled)
                            if (player is MediaController) {
                                val targetDb = if (enabled) currentBoostDb else 0
                                player.setCenterBoostDb(targetDb)
                            }
                        },
                    )
                    .fillMaxWidth()
                    .padding(8.dp)
                    .alpha(if (isCenterBoostSupported) 1f else 0.38f)
                    .semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "Center Channel Boost",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                NextSwitch(
                    checked = isCenterBoostEnabled && isCenterBoostSupported,
                    onCheckedChange = null,
                )
            }

            if (isCenterBoostEnabled) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .alpha(if (isCenterBoostSupported) 1f else 0.38f),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Gain",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = "+$currentBoostDb dB",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Slider(
                        value = currentBoostDb.toFloat(),
                        valueRange = 0f..20f,
                        steps = 19,
                        enabled = isCenterBoostSupported,
                        onValueChange = { newDb ->
                            val roundedDb = newDb.roundToInt()
                            currentBoostDb = roundedDb

                            if (player is MediaController) {
                                player.setCenterBoostDb(roundedDb)
                            }
                        },
                        onValueChangeFinished = {
                            onCenterBoostDbChange(currentBoostDb)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
