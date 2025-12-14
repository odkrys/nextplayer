package dev.anilbeesetti.nextplayer.navigation

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.navigation
import dev.anilbeesetti.nextplayer.core.model.Video
import dev.anilbeesetti.nextplayer.feature.player.extensions.EXTRA_PLAYLIST_MEDIA_URI
import dev.anilbeesetti.nextplayer.feature.player.extensions.EXTRA_PLAYLIST_START_INDEX
import dev.anilbeesetti.nextplayer.feature.player.extensions.EXTRA_PLAYLIST_URIS
import dev.anilbeesetti.nextplayer.feature.player.PlayerActivity
import dev.anilbeesetti.nextplayer.feature.videopicker.navigation.mediaPickerFolderScreen
import dev.anilbeesetti.nextplayer.feature.videopicker.navigation.mediaPickerNavigationRoute
import dev.anilbeesetti.nextplayer.feature.videopicker.navigation.mediaPickerScreen
import dev.anilbeesetti.nextplayer.feature.videopicker.navigation.navigateToMediaPickerFolderScreen
import dev.anilbeesetti.nextplayer.settings.navigation.navigateToSettings

const val MEDIA_ROUTE = "media_nav_route"

fun NavGraphBuilder.mediaNavGraph(
    context: Context,
    navController: NavHostController,
) {
    navigation(
        startDestination = mediaPickerNavigationRoute,
        route = MEDIA_ROUTE,
    ) {
        mediaPickerScreen(
            onPlayVideo = context::startPlayerActivity,
            onPlayPlaylist = context::startPlayerPlaylistActivity,
            onFolderClick = navController::navigateToMediaPickerFolderScreen,
            onSettingsClick = navController::navigateToSettings,
        )
        mediaPickerFolderScreen(
            onNavigateUp = navController::navigateUp,
            onVideoClick = context::startPlayerActivity,
            onFolderClick = navController::navigateToMediaPickerFolderScreen,
        )
    }
}

fun Context.startPlayerActivity(uri: Uri) {
    val intent = Intent(Intent.ACTION_VIEW, uri, this, PlayerActivity::class.java)
    startActivity(intent)
}

fun Context.startPlayerPlaylistActivity(
    playlist: List<Video>,
    startIndex: Int,
) {
    val mediaUri = playlist[startIndex].uriString

    val intent = Intent(this, PlayerActivity::class.java).apply {
        putStringArrayListExtra(
            EXTRA_PLAYLIST_URIS,
            ArrayList(playlist.map { it.uriString })
        )
        putExtra(EXTRA_PLAYLIST_MEDIA_URI, mediaUri)
        putExtra(EXTRA_PLAYLIST_START_INDEX, startIndex)
    }
    startActivity(intent)
}