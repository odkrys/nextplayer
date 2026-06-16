package dev.anilbeesetti.nextplayer.feature.player.buttons

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.media3.common.C

@Composable
fun AbRepeatButton(
    abRepeatA: Long,
    abRepeatB: Long,
    onClick: () -> Unit
) {
    val isSetABUI = abRepeatA != C.TIME_UNSET && abRepeatB != C.TIME_UNSET
    val isSetAOnlyUI = abRepeatA != C.TIME_UNSET && abRepeatB == C.TIME_UNSET
    val isActive = isSetABUI || isSetAOnlyUI

    val buttonText = when {
        isSetABUI -> "AB"
        isSetAOnlyUI -> "A.."
        else -> "AB"
    }

    PlayerActiveButton(
        isActive = isActive,
        onClick = onClick
    ) {
        Text(
            text = buttonText,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black
        )
    }
}
