package dev.anilbeesetti.nextplayer.feature.videopicker.navigation

import android.net.Uri
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import dev.anilbeesetti.nextplayer.feature.videopicker.screens.playlist.PlaylistRoute
import dev.anilbeesetti.nextplayer.feature.videopicker.screens.playlist.PlaylistDetailRoute

const val SELECTED_URIS_ARG = "selectedUris"
const val PLAYLIST_ROUTE = "playlist?$SELECTED_URIS_ARG={$SELECTED_URIS_ARG}"
const val PLAYLIST_DETAIL_ROUTE = "playlist/{${PLAYLIST_ID_ARG}}"

private const val URI_SEPARATOR = "|||"

fun NavController.navigateToPlaylist(selectedUris: List<String> = emptyList()) {
    val encodedUris = selectedUris.joinToString(URI_SEPARATOR) { Uri.encode(it) }
    navigate("playlist?$SELECTED_URIS_ARG=$encodedUris")
}

fun NavController.navigateToPlaylistDetail(playlistId: Long) {
    navigate("playlist/$playlistId")
}

fun NavGraphBuilder.playlistScreen(
    onPlaylistClick: (playlistId: Long, selectedUris: List<String>) -> Unit,
    onBackClick: () -> Unit,
) {
    composable(
        route = PLAYLIST_ROUTE,
        arguments = listOf(
            navArgument(SELECTED_URIS_ARG) {
                type = NavType.StringType
                defaultValue = ""
            },
        ),
    ) { backStackEntry ->
        val selectedUris = backStackEntry.arguments
            ?.getString(SELECTED_URIS_ARG)
            ?.let { Uri.decode(it) }
            ?.split(URI_SEPARATOR)
            ?.filter { it.isNotBlank() }
            ?.map { Uri.decode(it) }
            ?: emptyList()

        PlaylistRoute(
            selectedUris = selectedUris,
            onPlaylistClick = onPlaylistClick,
            onBackClick = onBackClick,
        )
    }
}

fun NavGraphBuilder.playlistDetailScreen(
    onPlayClick: (playlistId: Long, uris: List<String>, startIndex: Int) -> Unit,
    onVideoClick: (playlistId: Long, uris: List<String>, index: Int) -> Unit,
    onBackClick: () -> Unit,
) {
    composable(
        route = PLAYLIST_DETAIL_ROUTE,
        arguments = listOf(
            navArgument(PLAYLIST_ID_ARG) { type = NavType.LongType },
        ),
    ) { backStackEntry ->
        val playlistId = backStackEntry.arguments?.getLong(PLAYLIST_ID_ARG) ?: -1L

        PlaylistDetailRoute(
            onPlayClick = { uris, startIndex -> onPlayClick(playlistId, uris, startIndex) },
            onVideoClick = { uris, index -> onVideoClick(playlistId, uris, index) },
            onBackClick = onBackClick,
        )
    }
}
