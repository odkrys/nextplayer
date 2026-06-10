package dev.anilbeesetti.nextplayer.feature.player.ui

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import dev.anilbeesetti.nextplayer.core.ui.components.NextSwitch
import dev.anilbeesetti.nextplayer.feature.player.state.SleepTimerState
import kotlin.math.ceil

@OptIn(UnstableApi::class)
@Composable
fun BoxScope.SleepTimerView(
    modifier: Modifier = Modifier,
    show: Boolean,
    sleepTimerState: SleepTimerState,
    lastSleepTimerMinutes: Int,
) {
    val hapticFeedback = LocalHapticFeedback.current
    val remainingMs = sleepTimerState.remainingTimeMs
    val initialMinutes = sleepTimerState.initialMinutes

    var showCustomDialog by remember { mutableStateOf(false) }
    var inputText by remember { mutableStateOf("") }

    val presets = listOf(
        15 to "15m",
        30 to "30m",
        60 to "1h",
        120 to "2h",
    )
    val presetMinutesList = presets.map { it.first }
    val activeMinutes = if (remainingMs > 0L) initialMinutes else 0

    OverlayView(
        modifier = modifier,
        show = show,
        title = "Sleep Timer",
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            FlowRow(
                maxItemsInEachRow = 4,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            ) {
                presets.forEach { (minutes, label) ->
                    val isSelected = activeMinutes == minutes
                    val ms = minutes * 60 * 1000L

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(CircleShape)
                            .then(
                                if (isSelected) {
                                    Modifier.background(MaterialTheme.colorScheme.primary)
                                } else {
                                    Modifier.border(
                                        width = 1.dp,
                                        color = LocalContentColor.current.copy(alpha = 0.6f),
                                        shape = CircleShape,
                                    )
                                },
                            )
                            .clickable {
                                sleepTimerState.updateSleepTimer(ms)
                            }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.primary,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            val isCustomActive = activeMinutes > 0 && activeMinutes !in presetMinutesList

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
            ) {
                Text(
                    text = "Custom time",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .then(
                            if (isCustomActive) {
                                Modifier.background(MaterialTheme.colorScheme.primary)
                            } else {
                                Modifier.border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(6.dp),
                                )
                            },
                        )
                        .clickable {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            val lastCustom = if (lastSleepTimerMinutes > 0 && lastSleepTimerMinutes !in presetMinutesList) {
                                lastSleepTimerMinutes.toString()
                            } else {
                                ""
                            }
                            inputText = if (isCustomActive) activeMinutes.toString() else lastCustom
                            showCustomDialog = true
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (isCustomActive) "${activeMinutes}m" else "Set",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isCustomActive) Color.White else MaterialTheme.colorScheme.primary,
                    )
                }
            }

            if (showCustomDialog) {
                AlertDialog(
                    onDismissRequest = { showCustomDialog = false },
                    title = { Text(text = "Custom sleep timer (min)") },
                    text = {
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { newValue ->
                                inputText = newValue.filter { it.isDigit() }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            label = { Text("Minutes") },
                            placeholder = { Text("e.g. 90") },
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                val newMinutes = inputText.toIntOrNull() ?: 0
                                if (newMinutes > 0) {
                                    val ms = newMinutes * 60 * 1000L
                                    sleepTimerState.updateSleepTimer(ms)
                                }
                                showCustomDialog = false
                            },
                        ) {
                            Text("OK")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showCustomDialog = false }) {
                            Text("Cancel")
                        }
                    },
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            if (remainingMs > 0L) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Left: ${formatRemainingTimeForOverlay(remainingMs)}",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                    )

                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.error,
                                shape = CircleShape,
                            )
                            .clickable {
                                sleepTimerState.cancelSleepTimer()
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Stop",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            } else {
                val isPauseAtEndEnabled = sleepTimerState.isPauseAtEndEnabled

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            sleepTimerState.togglePauseAtEnd(!isPauseAtEndEnabled)
                        }
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Stop after current media",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    NextSwitch(
                        checked = isPauseAtEndEnabled,
                        onCheckedChange = null
                    )
                }
            }
        }
    }
}

private fun formatRemainingTimeForOverlay(millis: Long): String {
    if (millis <= 0) return "0m"
    val totalMinutes = ceil(millis / 60_000.0).toLong()
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60

    return when {
        hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
        hours > 0 -> "${hours}h"
        else -> "${minutes}m"
    }
}
