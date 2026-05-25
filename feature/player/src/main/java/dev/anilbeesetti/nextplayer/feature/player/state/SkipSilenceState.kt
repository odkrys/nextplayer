package dev.anilbeesetti.nextplayer.feature.player.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import dev.anilbeesetti.nextplayer.feature.player.service.getSkipSilenceEnabled
import dev.anilbeesetti.nextplayer.feature.player.service.setSkipSilenceEnabled
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@UnstableApi
@Composable
fun rememberSkipSilenceState(player: Player): SkipSilenceState {
    val scope = rememberCoroutineScope()
    val state = remember(player) { SkipSilenceState(player, scope) }
    LaunchedEffect(player) { state.observe() }
    return state
}

@UnstableApi
class SkipSilenceState(
    private val player: Player,
    private val scope: CoroutineScope,
) {
    var skipSilenceEnabled: Boolean by mutableStateOf(false)
        private set

    fun setSkipSilence(enabled: Boolean) {
        scope.launch {
            when (player) {
                is MediaController -> player.setSkipSilenceEnabled(enabled)
                is ExoPlayer -> player.skipSilenceEnabled = enabled
                else -> return@launch
            }
            updateState()
        }
    }

    suspend fun observe() {
        updateState()
    }

    private fun updateState() {
        scope.launch {
            skipSilenceEnabled = when (player) {
                is MediaController -> player.getSkipSilenceEnabled()
                is ExoPlayer -> player.skipSilenceEnabled
                else -> return@launch
            }
        }
    }
}
