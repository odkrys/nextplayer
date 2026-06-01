package dev.anilbeesetti.nextplayer.feature.videopicker.screens.playlist

import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.anilbeesetti.nextplayer.core.model.Playlist
import dev.anilbeesetti.nextplayer.core.ui.base.DataState
import dev.anilbeesetti.nextplayer.core.ui.base.LocalBottomBarVisibility
import dev.anilbeesetti.nextplayer.core.ui.components.NextTopAppBar
import dev.anilbeesetti.nextplayer.core.ui.designsystem.NextIcons
import sh.calvin.reorderable.DragGestureDetector
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun PlaylistRoute(
    selectedUris: List<String> = emptyList(),
    viewModel: PlaylistViewModel,
    onPlaylistClick: (Long, List<String>) -> Unit,
    onBackClick: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.playlistEvent.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            if (selectedUris.isNotEmpty()) {
                onBackClick()
            }
        }
    }

    PlaylistScreen(
        uiState = uiState,
        selectedUris = selectedUris,
        onPlaylistClick = onPlaylistClick,
        onBackClick = onBackClick,
        onEvent = viewModel::onEvent,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistScreen(
    uiState: PlaylistUiState,
    selectedUris: List<String> = emptyList(),
    onPlaylistClick: (Long, List<String>) -> Unit,
    onBackClick: () -> Unit,
    onEvent: (PlaylistUiEvent) -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var showCreateDialog by rememberSaveable { mutableStateOf(false) }

    var playlistToRename by remember { mutableStateOf<Playlist?>(null) }
    var playlistToDelete by remember { mutableStateOf<Playlist?>(null) }

    val isBottomBarVisible = LocalBottomBarVisibility.current
    val isAddingMode = selectedUris.isNotEmpty()

    LaunchedEffect(isAddingMode, isBottomBarVisible.value) {
        if (isAddingMode && isBottomBarVisible.value) {
            isBottomBarVisible.value = false
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            isBottomBarVisible.value = true
        }
    }

    Scaffold(
        topBar = {
            NextTopAppBar(
                title = if (isAddingMode) "Select a playlist" else "Playlist",
                fontWeight = if (!isAddingMode) FontWeight.Bold else null,
                navigationIcon = {
                    if (isAddingMode) {
                        IconButton(onClick = {
                            if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                                onBackClick()
                            }
                        }) {
                            Icon(NextIcons.Close, contentDescription = "Cancel")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { showCreateDialog = true }) {
                        Icon(NextIcons.Add, contentDescription = "Create playlist")
                    }
                }
            )
        },
    ) { paddingValues ->
        val layoutDirection = LocalLayoutDirection.current

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = paddingValues.calculateTopPadding(),
                    start = paddingValues.calculateStartPadding(layoutDirection),
                    end = paddingValues.calculateEndPadding(layoutDirection),
                    bottom = 0.dp
                )
        ) {
            when (val state = uiState.dataState) {
                is DataState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is DataState.Success -> {
                    if (state.value.isEmpty()) {
                        EmptyPlaylistContent(modifier = Modifier.align(Alignment.Center))
                    } else {
                        PlaylistContent(
                            playlists = state.value,
                            paddingValues = paddingValues,
                            onPlaylistClick = { playlistId ->
                                if (selectedUris.isNotEmpty()) {
                                    onEvent(PlaylistUiEvent.AddMediaToPlaylist(playlistId, selectedUris))
                                } else {
                                    onPlaylistClick(playlistId, emptyList<String>())
                                }
                            },
                            onRename = { playlistId, _ ->
                                playlistToRename = state.value.find { it.id == playlistId }
                            },
                            onDelete = { playlistId ->
                                playlistToDelete = state.value.find { it.id == playlistId }
                            },
                            onReorder = { ids -> onEvent(PlaylistUiEvent.ReorderPlaylists(ids)) },
                        )
                    }
                }
                is DataState.Error -> {
                    Text(
                        text = "Failed to load playlists",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        PlaylistNameDialog(
            title = "Create playlist",
            existingNames = (uiState.dataState as? DataState.Success)?.value?.map { it.name } ?: emptyList(),
            onConfirm = { name ->
                onEvent(PlaylistUiEvent.CreatePlaylist(name))
                showCreateDialog = false
            },
            onDismiss = { showCreateDialog = false },
        )
    }

    playlistToRename?.let { playlist ->
        PlaylistNameDialog(
            title = "Rename",
            initialName = playlist.name,
            existingNames = (uiState.dataState as? DataState.Success)?.value?.map { it.name } ?: emptyList(),
            onConfirm = { name ->
                onEvent(PlaylistUiEvent.RenamePlaylist(playlist.id, name))
                playlistToRename = null
            },
            onDismiss = { playlistToRename = null },
        )
    }

    playlistToDelete?.let { playlist ->
        AlertDialog(
            onDismissRequest = { playlistToDelete = null },
            title = { Text("Delete playlist") },
            text = { Text("Are you sure you want to delete '${playlist.name}'?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onEvent(PlaylistUiEvent.DeletePlaylist(playlist.id))
                        playlistToDelete = null
                    }
                ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { playlistToDelete = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun PlaylistContent(
    playlists: List<Playlist>,
    paddingValues: PaddingValues,
    onPlaylistClick: (Long) -> Unit,
    onRename: (Long, String) -> Unit,
    onDelete: (Long) -> Unit,
    onReorder: (List<Long>) -> Unit,
) {
    val hapticFeedback = LocalHapticFeedback.current
    val lazyListState = rememberLazyListState()

    var localPlaylists by remember { mutableStateOf(playlists) }
    val reorderableLazyListState = rememberReorderableLazyListState(lazyListState) { from, to ->
        localPlaylists = localPlaylists.toMutableList().apply {
            add(to.index, removeAt(from.index))
        }
        hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
    }

    LaunchedEffect(playlists) {
        if (!reorderableLazyListState.isAnyItemDragging) {
            localPlaylists = playlists
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { clip = false }
    ) {
        LazyColumn(
            state = lazyListState,
            contentPadding = PaddingValues(top = 16.dp, bottom = 60.dp, start = 16.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
            ) {
            items(items = localPlaylists, key = { it.id }) { playlist ->
                ReorderableItem(
                    state = reorderableLazyListState,
                    key = playlist.id,
                ) { isDragging ->
                    PlaylistItem(
                        playlist = playlist,
                        isDragging = isDragging,
                        onClick = { onPlaylistClick(playlist.id) },
                        onRename = { onRename(playlist.id, playlist.name) },
                        onDelete = { onDelete(playlist.id) },
                        onDragStopped = {
                            onReorder(localPlaylists.map { it.id })
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ReorderableCollectionItemScope.PlaylistItem(
    playlist: Playlist,
    isDragging: Boolean,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onDragStopped: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hapticFeedback = LocalHapticFeedback.current

    var showMenu by remember { mutableStateOf(false) }

    val backgroundColor by animateColorAsState(
        targetValue = if (isDragging) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        label = "drag_color"
    )

    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .draggableHandle(
                onDragStarted = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                },
                onDragStopped = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureEnd)
                    onDragStopped()
                },
                interactionSource = interactionSource,
                dragGestureDetector = DragGestureDetector.LongPress,
            )
            .clickable(onClick = onClick),
        elevation = CardDefaults.outlinedCardElevation(defaultElevation = if (isDragging) 8.dp else 0.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = backgroundColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = NextIcons.Bookmark,
                contentDescription = null,
                modifier = Modifier.padding(end = 24.dp),
                tint = MaterialTheme.colorScheme.primary,
            )

            Text(
                text = playlist.name,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .height(28.dp)
                    .widthIn(min = 28.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp),
                    )
                    .padding(horizontal = 6.dp)
            ) {
                Text(
                    text = playlist.mediaUris.size.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }

            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(NextIcons.MoreVert, contentDescription = "More options")
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        leadingIcon = { Icon(NextIcons.Edit, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onRename()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        leadingIcon = {
                            Icon(
                                NextIcons.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                        },
                        onClick = {
                            showMenu = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyPlaylistContent(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = NextIcons.Bookmark,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No playlists found",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Tap the + button to create a playlist",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        )
    }
}

@Composable
private fun PlaylistNameDialog(
    title: String,
    initialName: String = "",
    existingNames: List<String> = emptyList(),
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }

    val isDuplicate = existingNames
        .filter { it != initialName }
        .any { it.equals(name.trim(), ignoreCase = true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                placeholder = { Text("Enter playlist name") },
                isError = isDuplicate,
                supportingText = if (isDuplicate) {
                    { Text("A playlist with this name already exists") }
                } else null,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank() && !isDuplicate,
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
