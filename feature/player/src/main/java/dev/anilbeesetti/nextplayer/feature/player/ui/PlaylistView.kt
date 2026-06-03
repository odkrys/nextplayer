package dev.anilbeesetti.nextplayer.feature.player.ui

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import dev.anilbeesetti.nextplayer.core.common.Utils
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.components.NextSegmentedListItem
import dev.anilbeesetti.nextplayer.core.ui.designsystem.NextIcons
import dev.anilbeesetti.nextplayer.feature.player.buttons.LoopButton
import dev.anilbeesetti.nextplayer.feature.player.buttons.ShuffleButton
import dev.anilbeesetti.nextplayer.feature.player.state.rememberPlaylistState
import kotlinx.coroutines.launch
import sh.calvin.reorderable.DragGestureDetector
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(UnstableApi::class)
@Composable
fun BoxScope.PlaylistView(
    modifier: Modifier = Modifier,
    show: Boolean,
    player: Player,
) {
    val hapticFeedback = LocalHapticFeedback.current
    val playlistState = rememberPlaylistState(player)
    val lazyListState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val reorderableLazyListState = rememberReorderableLazyListState(lazyListState) { from, to ->
        playlistState.moveItem(from.index, to.index)
        hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
    }
/*
    // Auto-scroll to current item when playlist opens
    LaunchedEffect(show) {
        if (show && playlistState.playlist.isNotEmpty()) {
            val currentIndex = playlistState.currentMediaItemIndex
            if (currentIndex in playlistState.playlist.indices) {
                lazyListState.scrollToItem(currentIndex)
            }
        }
    }
*/
    LaunchedEffect(show, playlistState.scrollTrigger) {
        if (show && playlistState.currentMediaId != null) {
            val targetUiIndex = playlistState.displayItems.indexOfFirst { (_, item) ->
                item.mediaId == playlistState.currentMediaId
            }
            if (targetUiIndex != -1) {
                lazyListState.scrollToItem(targetUiIndex)
            }
        }
    }

    OverlayView(
        modifier = modifier,
        show = show,
        title = stringResource(R.string.now_playing),
        actions = {
            LoopButton(player = player, showControlsOnClick = false)
            ShuffleButton(player = player, showControlsOnClick = false)
        },
    ) {
        //if (playlistState.playlist.isEmpty()) {
        if (playlistState.displayItems.isEmpty()) {
            // Empty state
            EmptyPlaylistView()
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = lazyListState,
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                itemsIndexed(
/*
                    items = playlistState.playlist,
                    key = { _, item -> item.mediaId },
                ) { index, mediaItem ->
*/
                    items = playlistState.displayItems,
                    key = { _, (_, item) -> item.mediaId },
                ) { uiIndex, (realIndex, mediaItem) ->
                    ReorderableItem(
                        state = reorderableLazyListState,
                        key = mediaItem.mediaId,
                    ) {
                        //val isCurrentItem = index == playlistState.currentMediaItemIndex
                        val isCurrentItem = mediaItem.mediaId == playlistState.currentMediaId
                        PlaylistItemView(
                            modifier = Modifier.animateItem(),
                            mediaItem = mediaItem,
/*
                            index = index,
                            isFirstItem = index == 0,
                            isLastItem = index == playlistState.playlist.lastIndex,

*/
                            index = uiIndex,
                            isFirstItem = uiIndex == 0,
                            isLastItem = uiIndex == playlistState.displayItems.lastIndex,
                            isCurrentItem = isCurrentItem,
/*
                            canDelete = playlistState.playlist.size > 1,
                            onClick = { playlistState.seekToItem(index) },
                            onDelete = { playlistState.removeItem(index) },

*/
                            canDelete = playlistState.displayItems.size > 1,
                            onClick = { playlistState.seekToItem(realIndex) },
                            onDelete = {
                                val isLastItemDeleted = uiIndex == playlistState.displayItems.lastIndex
                                val wasPlaying = mediaItem.mediaId == playlistState.currentMediaId

                                playlistState.removeItem(mediaItem)

                                if (isLastItemDeleted && wasPlaying && playlistState.displayItems.isNotEmpty()) {
                                    scope.launch {
                                        lazyListState.scrollToItem(0)
                                    }
                                }
                            },
                            isShuffleEnabled = player.shuffleModeEnabled,
                            onDragStarted = { playlistState.isDragging = true },
                            onDragStopped = { playlistState.isDragging = false },
                        )
                    }
                }
            }
        }
    }
}

@kotlin.OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ReorderableCollectionItemScope.PlaylistItemView(
    modifier: Modifier,
    mediaItem: MediaItem,
    index: Int,
    isFirstItem: Boolean = false,
    isLastItem: Boolean = false,
    isCurrentItem: Boolean,
    canDelete: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    isShuffleEnabled: Boolean,
    onDragStarted: () -> Unit,
    onDragStopped: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hapticFeedback = LocalHapticFeedback.current
    val context = LocalContext.current

    NextSegmentedListItem(
        //modifier = Modifier
        modifier = modifier
            .fillMaxWidth()
            .draggableHandle(
                enabled = !isShuffleEnabled,
                onDragStarted = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                    onDragStarted()
                },
                onDragStopped = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureEnd)
                    onDragStopped()
                },
                interactionSource = interactionSource,
                dragGestureDetector = DragGestureDetector.LongPress,
            ),

        selected = isCurrentItem,
        contentPadding = PaddingValues(8.dp),
        interactionSource = interactionSource,
        colors = ListItemDefaults.segmentedColors(
            contentColor = if (isCurrentItem) {
                MaterialTheme.colorScheme.primary
            } else {
                ListItemDefaults.segmentedColors().contentColor
            },
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
        ),
        isFirstItem = isFirstItem,
        isLastItem = isLastItem,
        onClick = onClick,
        onLongClick = if (isShuffleEnabled) {
            {
                android.widget.Toast.makeText(
                    context,
                    "Cannot reorder while shuffle is enabled",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        } else null,
        leadingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
/*
                Icon(
                    painter = painterResource(R.drawable.ic_drag_handle),
                    contentDescription = stringResource(R.string.reorder),
                    tint = if (isCurrentItem) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
*/
                Text(
                    text = (index + 1).toString(),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isCurrentItem) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(28.dp)
                )

                ThumbnailView(
                    mediaItem = mediaItem,
                    modifier = Modifier
                        .width(min(100.dp, LocalConfiguration.current.screenWidthDp.dp * 0.3f)),
                )
            }
        },
        content = {
            Text(
                text = mediaItem.mediaMetadata.title?.toString() ?: stringResource(R.string.unknown),
                maxLines = 2,
                style = MaterialTheme.typography.titleSmall,
                color = if (isCurrentItem) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            if (canDelete) {
                IconButton(onClick = onDelete) {
                    Icon(
                        painter = painterResource(R.drawable.ic_close),
                        contentDescription = stringResource(R.string.remove),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    )
}

@Composable
private fun ThumbnailView(
    modifier: Modifier = Modifier,
    mediaItem: MediaItem,
) {
    val context = LocalContext.current
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
            .aspectRatio(16f / 10f),
    ) {
        // Fallback icon
        Icon(
            imageVector = NextIcons.Video,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.surfaceColorAtElevation(100.dp),
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxSize(0.5f),
        )

        // Thumbnail image
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(mediaItem.mediaId)
                .crossfade(true)
                .build(),
            contentDescription = null,
            alignment = Alignment.Center,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        // Duration overlay
        mediaItem.mediaMetadata.durationMs?.let { durationMs ->
            if (durationMs > 0) {
                Text(
                    text = Utils.formatDurationMillis(durationMs),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Normal),
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(vertical = 1.dp, horizontal = 3.dp),
                )
            }
        }
    }
}

@Composable
private fun EmptyPlaylistView() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            imageVector = NextIcons.Video,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.fillMaxSize(0.3f),
        )
        Text(
            text = stringResource(R.string.no_videos_in_queue),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
