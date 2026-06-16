package dev.anilbeesetti.nextplayer.feature.player.buttons

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import dev.anilbeesetti.nextplayer.core.ui.designsystem.NextIcons

@Composable
fun SleepTimerButton(
    isActive: Boolean,
    onClick: () -> Unit
) {
    PlayerActiveButton(
        isActive = isActive,
        onClick = onClick
    ) {
        Icon(
            imageVector = NextIcons.Snooze,
            contentDescription = "Sleep Timer",
        )
    }
}
