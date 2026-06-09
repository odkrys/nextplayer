package dev.anilbeesetti.nextplayer.feature.player.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import dev.anilbeesetti.nextplayer.feature.player.service.cancelSleepTimer
import dev.anilbeesetti.nextplayer.feature.player.service.getSleepTimerState
import dev.anilbeesetti.nextplayer.feature.player.service.setPauseAtEndOnce
import dev.anilbeesetti.nextplayer.feature.player.service.setSleepTimer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.ceil

@UnstableApi
@Composable
fun rememberSleepTimerState(player: Player): SleepTimerState {
    val scope = rememberCoroutineScope()
    val state = remember(player) { SleepTimerState(player, scope) }
    LaunchedEffect(player) { state.observe() }
    return state
}

@UnstableApi
class SleepTimerState(
    private val player: Player,
    private val scope: CoroutineScope,
) {
    var remainingTimeMs: Long by mutableLongStateOf(0L)
        private set

    var formattedRemainingTime: String by mutableStateOf("")
        private set

    var initialMinutes: Int by mutableIntStateOf(0)
        private set

    var isPauseAtEndEnabled: Boolean by mutableStateOf(false)
        private set

    val isActive: Boolean
        get() = remainingTimeMs > 0L || isPauseAtEndEnabled

    val badgeText: String
        get() = when {
            remainingTimeMs > 0L -> formattedRemainingTime
            isPauseAtEndEnabled -> "Last"
            else -> ""
        }

    fun updateSleepTimer(durationMs: Long) {
        scope.launch {
            if (player is MediaController) {
                player.setSleepTimer(durationMs)
                fetchAndUpdateState()
            }
        }
    }

    fun cancelSleepTimer() {
        scope.launch {
            if (player is MediaController) {
                player.cancelSleepTimer()
                fetchAndUpdateState()
            }
        }
    }

    fun togglePauseAtEnd(enabled: Boolean) {
        scope.launch {
            if (player is MediaController) {
                player.setPauseAtEndOnce(enabled)
                fetchAndUpdateState()
            }
        }
    }

    suspend fun observe() {
        while (scope.isActive) {
            fetchAndUpdateState()
            val interval = when {
                remainingTimeMs in 1..60_000 -> 1_000L
                else -> 3_000L
            }
            delay(interval)
        }
    }

    private suspend fun fetchAndUpdateState() {
        if (player is MediaController) {
            val (remaining, initialDuration, pauseAtEnd) = player.getSleepTimerState()

            isPauseAtEndEnabled = pauseAtEnd
            remainingTimeMs = remaining
            formattedRemainingTime = formatRemainingTime(remaining)
            initialMinutes = (initialDuration / (60 * 1000L)).toInt()
        }
    }

    private fun formatRemainingTime(millis: Long): String {
        if (millis <= 0) return ""

        val totalMinutes = ceil(millis / 60_000.0).toLong()
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60

        return when {
            hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
            hours > 0 -> "${hours}h"
            minutes > 0 -> "${minutes}m"
            else -> ""
        }
    }
}
