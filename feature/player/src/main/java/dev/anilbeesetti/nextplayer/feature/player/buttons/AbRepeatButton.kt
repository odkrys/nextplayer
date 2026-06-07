package dev.anilbeesetti.nextplayer.feature.player.buttons

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

    val buttonText = when {
        isSetABUI -> "AB"
        isSetAOnlyUI -> "A.."
        else -> "AB"
    }

    if (useMaterialYou) {
        PlayerButton(onClick = onClick) {
            Text(
                text = buttonText,
                color = if (isActive) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black
            )
        }
    } else {
        CompositionLocalProvider(
            LocalRippleConfiguration provides RippleConfiguration(
                color = Color.White,
                rippleAlpha = RippleAlpha(
                    pressedAlpha = 0.5f,
                    focusedAlpha = 0.5f,
                    draggedAlpha = 0.5f,
                    hoveredAlpha = 0.5f
                )
            )
        ) {
            Surface(
                onClick = onClick,
                shape = CircleShape,
                color = if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.8f) else Color.Transparent,
                contentColor = Color.White,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = buttonText,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }
    }
}
