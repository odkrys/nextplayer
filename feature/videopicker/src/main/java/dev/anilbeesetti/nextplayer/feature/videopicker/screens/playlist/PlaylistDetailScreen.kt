package dev.anilbeesetti.nextplayer.feature.videopicker.screens.playlist

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.anilbeesetti.nextplayer.core.model.ApplicationPreferences
import dev.anilbeesetti.nextplayer.core.model.Video
import dev.anilbeesetti.nextplayer.core.ui.base.DataState
import dev.anilbeesetti.nextplayer.core.ui.designsystem.NextIcons
import dev.anilbeesetti.nextplayer.feature.videopicker.composables.VideoItem

@Composable
fun PlaylistDetailRoute(
    onPlayClick: (uris: List<String>, startIndex: Int) -> Unit,
    onVideoClick: (uris: List<String>, index: Int) -> Unit,
    onBackClick: () -> Unit,
    viewModel: PlaylistDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    PlaylistDetailScreen(
        uiState = uiState,
        onPlayClick = { uris ->
            val startIndex = viewModel.getRecentVideoIndex()
            viewModel.saveLastPlayed(uris[startIndex])
            onPlayClick(uris, startIndex)
        },
        onVideoClick = { uris, index ->
            viewModel.saveLastPlayed(uris[index])
            onVideoClick(uris, index)
        },
        onBackClick = onBackClick,
        onEvent = viewModel::onEvent,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    uiState: PlaylistDetailUiState,
    onPlayClick: (List<String>) -> Unit,
    onVideoClick: (List<String>, Int) -> Unit,
    onBackClick: () -> Unit,
    onEvent: (PlaylistDetailUiEvent) -> Unit,
) {
    val playlist = (uiState.dataState as? DataState.Success)?.value
    var showAddMediaSheet by rememberSaveable { mutableStateOf(false) }
    var isSelectionMode by rememberSaveable { mutableStateOf(false) }
    var selectedUris by rememberSaveable { mutableStateOf(emptySet<String>()) }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    var showSortMenu by rememberSaveable { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val isAllSelected = uiState.sortedVideos.isNotEmpty() && selectedUris.size == uiState.sortedVideos.size

    LaunchedEffect(uiState.sortOption) {
        if (uiState.sortedVideos.isNotEmpty()) {
            listState.scrollToItem(0)
        }
    }

    BackHandler(enabled = isSelectionMode) {
        isSelectionMode = false
        selectedUris = emptySet()
    }

    fun exitSelectionMode() {
        isSelectionMode = false
        selectedUris = emptySet()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isSelectionMode) {
                        Text("${selectedUris.size} selected")
                    } else {
                        Text(playlist?.name ?: "")
                    }
                },
                navigationIcon = {
                    if (isSelectionMode) {
                        IconButton(onClick = { exitSelectionMode() }) {
                            Icon(NextIcons.Close, contentDescription = "Clear selection")
                        }
                    } else {
                        IconButton(onClick = onBackClick) {
                            Icon(NextIcons.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    if (isSelectionMode) {
                        IconButton(
                            onClick = {
                                if (isAllSelected) {
                                    selectedUris = emptySet()
                                } else {
                                    selectedUris = uiState.sortedVideos.map { it.uriString }.toSet()
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (isAllSelected) NextIcons.DeselectAll else NextIcons.SelectAll,
                                contentDescription = if (isAllSelected) "Select all" else "Deselect all"
                            )
                        }

                        IconButton(
                            onClick = { showDeleteDialog = true },
                            enabled = selectedUris.isNotEmpty(),
                        ) {
                            Icon(
                                NextIcons.Delete,
                                contentDescription = "Remove selected items",
                                tint = if (selectedUris.isNotEmpty())
                                    MaterialTheme.colorScheme.error
                                else
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                            )
                        }
                    } else {
                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(NextIcons.Sort, contentDescription = "Sort")
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Date added (Oldest)") },
                                    onClick = {
                                        onEvent(PlaylistDetailUiEvent.UpdateSortOption(PlaylistSortOption.ADDED_ASC))
                                        showSortMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Date added (Newest)") },
                                    onClick = {
                                        onEvent(PlaylistDetailUiEvent.UpdateSortOption(PlaylistSortOption.ADDED_DESC))
                                        showSortMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Name (A-Z)") },
                                    onClick = {
                                        onEvent(PlaylistDetailUiEvent.UpdateSortOption(PlaylistSortOption.NAME_ASC))
                                        showSortMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Name (Z-A)") },
                                    onClick = {
                                        onEvent(PlaylistDetailUiEvent.UpdateSortOption(PlaylistSortOption.NAME_DESC))
                                        showSortMenu = false
                                    }
                                )
                            }
                        }
                        IconButton(onClick = { showAddMediaSheet = true }) {
                            Icon(NextIcons.Add, contentDescription = "Add media")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (!isSelectionMode && uiState.sortedVideos.isNotEmpty()) {
                androidx.compose.material3.FloatingActionButton(
                    onClick = { onPlayClick(uiState.sortedVideos.map { it.uriString }) },
                    modifier = Modifier.padding(end = 16.dp, bottom = 16.dp)
                ) {
                    Icon(imageVector = NextIcons.Play, contentDescription = "Play playlist")
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            when (val state = uiState.dataState) {
                is DataState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is DataState.Success -> {
                    val playlistData = state.value
                    if (playlistData == null || playlistData.mediaUris.isEmpty()) {
                        Text(
                            text = "Playlist is empty",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    } else {
                        PlaylistDetailContent(
                            videos = uiState.sortedVideos,
                            lastPlayedUri = playlistData.lastPlayedUri,
                            preferences = uiState.preferences,
                            isSelectionMode = isSelectionMode,
                            selectedUris = selectedUris,
                            listState = listState,
                            onVideoClick = { index ->
                                if (isSelectionMode) {
                                    val uri = uiState.sortedVideos[index].uriString
                                    selectedUris = if (uri in selectedUris) selectedUris - uri else selectedUris + uri
                                    if (selectedUris.isEmpty()) exitSelectionMode()
                                } else {
                                    onVideoClick(uiState.sortedVideos.map { it.uriString }, index)
                                }
                            },
                            onVideoLongClick = { index ->
                                if (!isSelectionMode) {
                                    isSelectionMode = true
                                    selectedUris = setOf(uiState.sortedVideos[index].uriString)
                                }
                            },
                        )
                    }
                }
                is DataState.Error -> {
                    Text(
                        text = "Failed to load playlist",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }
        }

        if (showAddMediaSheet) {
            val playlistData = (uiState.dataState as? DataState.Success)?.value

            playlistData?.id?.let { currentId ->
                AddMediaToPlaylistBottomSheet(
                    playlistId = currentId,
                    onDismiss = { showAddMediaSheet = false },
                )
            }
        }

        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text("Confirm removal") },
                text = {
                    Text(
                        if (selectedUris.size == 1) {
                            "Remove the selected item from this playlist?"
                        } else {
                            "Remove ${selectedUris.size} selected items from this playlist?"
                        },
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            selectedUris.forEach { uri ->
                                onEvent(PlaylistDetailUiEvent.RemoveMedium(uri))
                            }
                            showDeleteDialog = false
                            exitSelectionMode()
                        },
                    ) {
                        Text("Remove", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) {
                        Text("Cancel")
                    }
                },
            )
        }
    }
}

@Composable
private fun PlaylistDetailContent(
    videos: List<Video>,
    lastPlayedUri: String?,
    preferences: ApplicationPreferences,
    isSelectionMode: Boolean,
    selectedUris: Set<String>,
    onVideoClick: (index: Int) -> Unit,
    onVideoLongClick: (index: Int) -> Unit,
    listState: LazyListState = rememberLazyListState()
) {
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        itemsIndexed(items = videos, key = { _, v -> v.uriString }) { index, video ->
            val isSelected = video.uriString in selectedUris
            val isRecentlyPlayed = video.uriString == lastPlayedUri

            VideoItem(
                video = video,
                isRecentlyPlayedVideo = isRecentlyPlayed,
                preferences = preferences,
                modifier = Modifier
                    .animateItem()
                    .fillMaxWidth(),
                isFirstItem = index == 0,
                isLastItem = index == videos.lastIndex,
                selected = isSelected,
                onClick = { onVideoClick(index) },
                onLongClick = { onVideoLongClick(index) },
            )
        }
    }
}
