package dev.anilbeesetti.nextplayer.feature.player.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.listen
import androidx.media3.common.util.UnstableApi
import dev.anilbeesetti.nextplayer.feature.player.extensions.getPlaybackOrderIndices

@UnstableApi
@Composable
fun rememberPlaylistState(player: Player): PlaylistState {
    val playlistState = remember { PlaylistState(player) }
    LaunchedEffect(player) { playlistState.observe() }
    return playlistState
}

class PlaylistState(
    private val player: Player,
) {
/*
    var playlist: List<MediaItem> by mutableStateOf(emptyList())
        private set

    var currentMediaItemIndex: Int by mutableStateOf(0)
        private set

    suspend fun observe() {
        updatePlaylist()
        updateCurrentIndex()

        player.listen { events ->
            if (events.containsAny(Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_TIMELINE_CHANGED)) {
                updatePlaylist()
            }
            if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) {
                updateCurrentIndex()
            }
        }
    }

    fun moveItem(fromIndex: Int, toIndex: Int) {
        if (fromIndex == toIndex) return
        if (fromIndex !in playlist.indices || toIndex !in playlist.indices) return

        player.moveMediaItem(fromIndex, toIndex)
        updatePlaylist()
        updateCurrentIndex()
    }

    fun removeItem(index: Int) {
        if (index !in playlist.indices) return
        if (playlist.size <= 1) return // Don't remove the last item

        player.removeMediaItem(index)
        updatePlaylist()
        updateCurrentIndex()
    }

    fun seekToItem(index: Int) {
        if (index !in playlist.indices) return
        if (player.currentMediaItemIndex == index) return
        player.seekToDefaultPosition(index)
        updateCurrentIndex()
    }

    private fun updatePlaylist() {
        val items = (0 until player.mediaItemCount).map { player.getMediaItemAt(it) }
        playlist = items
    }

    private fun updateCurrentIndex() {
        currentMediaItemIndex = player.currentMediaItemIndex
    }
*/
    var displayItems: List<Pair<Int, MediaItem>> by mutableStateOf(emptyList())
        private set

    var currentMediaId: String? by mutableStateOf(null)
        private set

    var isDragging: Boolean = false

    suspend fun observe() {
        syncPlaylist()
        updateCurrentId()

        player.listen { events ->
            if (events.contains(Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED) || events.contains(Player.EVENT_TIMELINE_CHANGED)) {
                syncPlaylist()
            }

            if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) {
                updateCurrentId()
            }
        }
    }

    fun moveItem(fromUiIndex: Int, toUiIndex: Int) {
        val fromRealIndex = displayItems.getOrNull(fromUiIndex)?.first ?: return
        val toRealIndex = displayItems.getOrNull(toUiIndex)?.first ?: return

        val mutableItems = displayItems.toMutableList()
        val itemToMove = mutableItems.removeAt(fromUiIndex)
        mutableItems.add(toUiIndex, itemToMove)
        displayItems = mutableItems

        player.moveMediaItem(fromRealIndex, toRealIndex)
    }

    fun removeItem(mediaItem: MediaItem) {
        val targetUiIndex = displayItems.indexOfFirst { it.second.mediaId == mediaItem.mediaId }
        if (targetUiIndex == -1) return

        val targetRealIndex = displayItems[targetUiIndex].first
        val isCurrentItem = mediaItem.mediaId == currentMediaId

        displayItems = displayItems
            .filterNot { it.second.mediaId == mediaItem.mediaId }
            .map { (realIdx, item) ->
                if (realIdx > targetRealIndex) Pair(realIdx - 1, item)
                else Pair(realIdx, item)
            }

        if (isCurrentItem) {
            if (player.hasNextMediaItem()) {
                player.seekToNext()
            }
        }

        player.removeMediaItem(targetRealIndex)
    }

    fun seekToItem(realIndex: Int) {
        player.seekToDefaultPosition(realIndex)
    }

    private fun syncPlaylist() {
        if (isDragging || player.currentTimeline.isEmpty || player.playbackState == Player.STATE_IDLE) {
            return
        }

        try {
            val orderIndices = player.getPlaybackOrderIndices()

            if (orderIndices.isEmpty()) {
                displayItems = emptyList()
                return
            }

            if (player.shuffleModeEnabled && orderIndices.size > 2) {
                var isFakeSequential = true
                for (i in orderIndices.indices) {
                    if (orderIndices[i] != i) {
                        isFakeSequential = false
                        break
                    }
                }
                if (isFakeSequential) {
                    return
                }
            }

            displayItems = orderIndices.mapNotNull { realIndex ->
                if (realIndex in 0 until player.mediaItemCount) {
                    Pair(realIndex, player.getMediaItemAt(realIndex))
                } else null
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateCurrentId() {
        currentMediaId = player.currentMediaItem?.mediaId
    }
}
