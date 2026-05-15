package dev.anilbeesetti.nextplayer.feature.player.buttons

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.anilbeesetti.nextplayer.core.ui.designsystem.NextIcons
import dev.anilbeesetti.nextplayer.feature.player.service.DlnaManager

@Composable
fun DlnaCastButton(
    devices: List<DlnaManager.DlnaDevice>,
    isSearching: Boolean,
    isCasting: Boolean,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onOpen: () -> Unit,
    onDeviceSelected: (DlnaManager.DlnaDevice) -> Unit,
    onStopCasting: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        PlayerButton(
            onClick = {
                if (isCasting) {
                    onStopCasting()
                } else {
                    onOpen()
                    onExpandedChange(true)
                }
            }
        ) {
            Icon(
                imageVector = if (isCasting) NextIcons.CastConnected else NextIcons.Cast,
                contentDescription = if (isCasting) "Stop casting" else "Cast",
                tint = if (isCasting) MaterialTheme.colorScheme.primary else Color.White,
                modifier = Modifier.size(24.dp)
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) }
        ) {
            if (isSearching) {
                DropdownMenuItem(
                    text = { Text("Searching...") },
                    leadingIcon = { CircularProgressIndicator(Modifier.size(16.dp)) },
                    onClick = {}
                )
            } else if (devices.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("No devices found") },
                    onClick = { onExpandedChange(false) }
                )
            } else {
                devices.forEach { device ->
                    DropdownMenuItem(
                        text = { Text(device.name) },
                        leadingIcon = {
                            Icon(
                                imageVector = if (device.isTV) NextIcons.Tv else NextIcons.Devices,
                                contentDescription = null
                            )
                        },
                        onClick = { onDeviceSelected(device) }
                    )
                }
            }
        }
    }
}
