package dev.anilbeesetti.nextplayer.feature.videopicker.screens.playlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.anilbeesetti.nextplayer.core.model.Video
import dev.anilbeesetti.nextplayer.core.ui.base.DataState
import dev.anilbeesetti.nextplayer.core.ui.designsystem.NextIcons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMediaToPlaylistBottomSheet(
    playlistId: Long,
    onDismiss: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    viewModel: AddMediaToPlaylistViewModel = hiltViewModel(),
) {
    LaunchedEffect(playlistId) {
        viewModel.initData(playlistId)
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.isDone) {
        if (uiState.isDone) {
            onDismiss()
            viewModel.onEvent(AddMediaToPlaylistUiEvent.ResetState)
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            onDismiss()
            viewModel.onEvent(AddMediaToPlaylistUiEvent.ResetState)
        },
        sheetState = sheetState,
    ) {
        AddMediaToPlaylistContent(
            uiState = uiState,
            onEvent = viewModel::onEvent,
            onDismiss = {
                onDismiss()
                viewModel.onEvent(AddMediaToPlaylistUiEvent.ResetState)
            },
        )
    }
}

@Composable
private fun AddMediaToPlaylistContent(
    uiState: AddMediaToPlaylistUiState,
    onEvent: (AddMediaToPlaylistUiEvent) -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (uiState.selectedCount > 0) {
                    "${uiState.selectedCount} selected "
                } else {
                    "Add media"
                },
                style = MaterialTheme.typography.titleMedium,
            )
            IconButton(onClick = onDismiss) {
                Icon(NextIcons.Close, contentDescription = "Close")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = uiState.searchQuery,
            onValueChange = { onEvent(AddMediaToPlaylistUiEvent.UpdateSearchQuery(it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search files") },
            leadingIcon = { Icon(NextIcons.Search, contentDescription = null) },
            singleLine = true,
        )

        Spacer(modifier = Modifier.height(8.dp))

        when (uiState.dataState) {
            is DataState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            is DataState.Success -> {
                if (uiState.filteredVideos.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (uiState.searchQuery.isBlank()) {
                                "No media available to add"
                            } else {
                                "No search results"
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 4.dp),
                    ) {
                        items(
                            items = uiState.filteredVideos,
                            key = { it.uriString },
                        ) { video ->
                            MediaSelectItem(
                                video = video,
                                isSelected = video.uriString in uiState.selectedUris,
                                onToggle = {
                                    onEvent(AddMediaToPlaylistUiEvent.ToggleSelection(video.uriString))
                                },
                            )
                        }
                    }
                }
            }
            is DataState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Failed to load media",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = { onEvent(AddMediaToPlaylistUiEvent.Confirm) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            enabled = uiState.selectedCount > 0,
        ) {
            Text(
                text = if (uiState.selectedCount > 0) {
                    "Add ${uiState.selectedCount}"
                } else {
                    "Add"
                },
            )
        }
    }
}

@Composable
private fun MediaSelectItem(
    video: Video,
    isSelected: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = NextIcons.Movie1,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = video.displayName,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = video.formattedDuration,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Icon(
            imageVector = if (isSelected) {
                NextIcons.CheckBox
            } else {
                NextIcons.CheckBoxOutline
            },
            contentDescription = if (isSelected) "Selected" else "Not selected",
            tint = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}
