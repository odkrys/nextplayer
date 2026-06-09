package dev.anilbeesetti.nextplayer.feature.player.buttons

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.anilbeesetti.nextplayer.core.ui.designsystem.NextIcons
import dev.anilbeesetti.nextplayer.feature.player.LocalUseMaterialYouControls

@Composable
fun SleepTimerButton(
    isActive: Boolean,
    onClick: () -> Unit
) {
    val useMaterialYou = LocalUseMaterialYouControls.current

    if (useMaterialYou) {
        PlayerButton(onClick = onClick) {
            Icon(
                imageVector = NextIcons.Snooze,
                contentDescription = "Sleep Timer",
                tint = if (isActive) MaterialTheme.colorScheme.primary else Color.White
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
                    Icon(
                        imageVector = NextIcons.Snooze,
                        contentDescription = "Sleep Timer",
                    )
                }
            }
        }
    }
}
