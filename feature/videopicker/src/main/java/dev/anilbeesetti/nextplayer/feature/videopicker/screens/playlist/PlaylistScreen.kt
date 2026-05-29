package dev.anilbeesetti.nextplayer.feature.videopicker.screens.playlist

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.anilbeesetti.nextplayer.core.model.Playlist
import dev.anilbeesetti.nextplayer.core.ui.base.DataState
import dev.anilbeesetti.nextplayer.core.ui.designsystem.NextIcons

@Composable
fun PlaylistRoute(
    selectedUris: List<String> = emptyList(),
    onPlaylistClick: (Long, List<String>) -> Unit,
    onBackClick: () -> Unit,
    viewModel: PlaylistViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(uiState.isDone) {
        if (uiState.isDone) {
            if (selectedUris.isNotEmpty()) {
                Toast.makeText(
                    context,
                    "Added ${selectedUris.size} videos to the playlist",
                    Toast.LENGTH_SHORT
                ).show()
            }
            onBackClick()
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
    var showCreateDialog by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Playlist") },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                modifier = Modifier.padding(end = 16.dp, bottom = 16.dp)
            ) {
                Icon(imageVector = NextIcons.Add, contentDescription = "Create playlist")
            }
        },
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
                    if (state.value.isEmpty()) {
                        EmptyPlaylistContent(modifier = Modifier.align(Alignment.Center))
                    } else {
                        PlaylistContent(
                            playlists = state.value,
                            onPlaylistClick = { playlistId ->
                                if (selectedUris.isNotEmpty()) {
                                    onEvent(PlaylistUiEvent.AddMediaToPlaylist(playlistId, selectedUris))
                                } else {
                                    onPlaylistClick(playlistId, emptyList<String>())
                                }
                            },
                            onRename = { id, name ->
                                onEvent(PlaylistUiEvent.RenamePlaylist(id, name))
                            },
                            onDelete = { id ->
                                onEvent(PlaylistUiEvent.DeletePlaylist(id))
                            },
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
            onConfirm = { name ->
                onEvent(PlaylistUiEvent.CreatePlaylist(name))
                showCreateDialog = false
            },
            onDismiss = { showCreateDialog = false },
        )
    }
}

@Composable
private fun PlaylistContent(
    playlists: List<Playlist>,
    onPlaylistClick: (Long) -> Unit,
    onRename: (Long, String) -> Unit,
    onDelete: (Long) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        items(items = playlists, key = { it.id }) { playlist ->
            PlaylistItem(
                playlist = playlist,
                onClick = { onPlaylistClick(playlist.id) },
                onRename = { name -> onRename(playlist.id, name) },
                onDelete = { onDelete(playlist.id) },
            )
        }
    }
}

@Composable
private fun PlaylistItem(
    playlist: Playlist,
    onClick: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    var showRenameDialog by rememberSaveable { mutableStateOf(false) }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = NextIcons.Bookmark,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(end = 16.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = "${playlist.mediaUris.size} items",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        showRenameDialog = true
                    },
                )
                DropdownMenuItem(
                    text = { Text("Delete") },
                    leadingIcon = { Icon(NextIcons.Delete, contentDescription = null) },
                    onClick = {
                        showMenu = false
                        showDeleteDialog = true
                    },
                )
            }
        }
    }

    if (showRenameDialog) {
        PlaylistNameDialog(
            title = "Rename",
            initialName = playlist.name,
            onConfirm = { name ->
                onRename(name)
                showRenameDialog = false
            },
            onDismiss = { showRenameDialog = false },
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete playlist") },
            text = {
                Text(
                    text = "Delete '${playlist.name}'?"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteDialog = false
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun EmptyPlaylistContent(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "No playlists found",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "Tap + to create playlist",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PlaylistNameDialog(
    title: String,
    initialName: String = "",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                placeholder = { Text("Enter playlist name") },
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank(),
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
