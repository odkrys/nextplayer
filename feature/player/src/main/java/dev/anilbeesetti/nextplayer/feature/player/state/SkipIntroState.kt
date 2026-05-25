package dev.anilbeesetti.nextplayer.feature.player.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import dev.anilbeesetti.nextplayer.feature.player.service.getSkipIntroEnabled
import dev.anilbeesetti.nextplayer.feature.player.service.setSkipIntroEnabled
import dev.anilbeesetti.nextplayer.feature.player.service.getSkipIntroTime
import dev.anilbeesetti.nextplayer.feature.player.service.setSkipIntroTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@UnstableApi
@Composable
fun rememberSkipIntroState(player: Player): SkipIntroState {
    val scope = rememberCoroutineScope()
    val state = remember(player) { SkipIntroState(player, scope) }
    LaunchedEffect(player) { state.observe() }
    return state
}

@UnstableApi
class SkipIntroState(
    private val player: Player,
    private val scope: CoroutineScope,
) {
    var skipIntroEnabled: Boolean by mutableStateOf(false)
        private set

    var skipIntroTime: Int by mutableIntStateOf(0)
        private set

    fun updateSkipIntroEnabled(enabled: Boolean) {
        skipIntroEnabled = enabled

        scope.launch {
            if (player is MediaController) player.setSkipIntroEnabled(enabled)
        }
    }

    fun updateSkipIntroTime(time: Int) {
        val safeTime = time.coerceAtLeast(0)

        skipIntroTime = safeTime

        scope.launch {
            if (player is MediaController) player.setSkipIntroTime(safeTime)
        }
    }

    fun observe() {
        updateState()
    }

    private fun updateState() {
        scope.launch {
            if (player is MediaController) {
                skipIntroEnabled = player.getSkipIntroEnabled()
                skipIntroTime = player.getSkipIntroTime()
            }
        }
    }
}