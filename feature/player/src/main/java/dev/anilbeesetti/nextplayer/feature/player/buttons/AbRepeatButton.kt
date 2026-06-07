package dev.anilbeesetti.nextplayer.feature.player.buttons

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import dev.anilbeesetti.nextplayer.feature.player.LocalUseMaterialYouControls

@Composable
fun AbRepeatButton(
    abRepeatA: Long,
    abRepeatB: Long,
    onClick: () -> Unit
) {
    val isSetABUI = abRepeatA != C.TIME_UNSET && abRepeatB != C.TIME_UNSET
    val isSetAOnlyUI = abRepeatA != C.TIME_UNSET && abRepeatB == C.TIME_UNSET
    val isActive = isSetABUI || isSetAOnlyUI

    val useMaterialYou = LocalUseMaterialYouControls.current

    val containerColor = when {
        useMaterialYou && isActive -> MaterialTheme.colorScheme.primary
        useMaterialYou && !isActive -> MaterialTheme.colorScheme.secondaryContainer
        !useMaterialYou && isActive -> MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
        else -> Color.Transparent
    }

    val contentColor = when {
        useMaterialYou && isActive -> MaterialTheme.colorScheme.onPrimary
        useMaterialYou && !isActive -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> Color.White
    }

    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(color = containerColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = when {
                isSetABUI -> "AB"
                isSetAOnlyUI -> "A.."
                else -> "AB"
            },
            color = contentColor,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black
        )
    }
}
