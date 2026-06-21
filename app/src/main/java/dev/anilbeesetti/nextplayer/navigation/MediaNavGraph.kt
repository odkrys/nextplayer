package dev.anilbeesetti.nextplayer.navigation

import android.content.Context
import android.content.Intent
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.navigation
import dev.anilbeesetti.nextplayer.feature.player.PlayerActivity
import dev.anilbeesetti.nextplayer.feature.player.service.PlayerService
import dev.anilbeesetti.nextplayer.feature.player.utils.PlayerApi
import dev.anilbeesetti.nextplayer.feature.videopicker.navigation.navigateToPlaylistDetail
import dev.anilbeesetti.nextplayer.feature.videopicker.navigation.playlistDetailScreen
import dev.anilbeesetti.nextplayer.feature.videopicker.navigation.playlistScreen
import dev.anilbeesetti.nextplayer.feature.videopicker.navigation.MediaPickerRoute
import dev.anilbeesetti.nextplayer.feature.videopicker.navigation.mediaPickerScreen
import dev.anilbeesetti.nextplayer.feature.videopicker.navigation.navigateToMediaPickerScreen
import dev.anilbeesetti.nextplayer.feature.videopicker.navigation.navigateToPlaylist
import dev.anilbeesetti.nextplayer.feature.videopicker.navigation.navigateToSearch
import dev.anilbeesetti.nextplayer.feature.videopicker.navigation.searchScreen
import dev.anilbeesetti.nextplayer.feature.videopicker.navigation.webdavNavGraph
import dev.anilbeesetti.nextplayer.settings.navigation.navigateToSettings
import kotlinx.serialization.Serializable
import okhttp3.Credentials

@Serializable
data object MediaRootRoute

fun NavGraphBuilder.mediaNavGraph(
    context: Context,
    navController: NavHostController,
) {
    navigation<MediaRootRoute>(startDestination = MediaPickerRoute()) {
        mediaPickerScreen(
            onNavigateUp = navController::navigateUp,
            onPlayVideo = { uri ->
                val intent = Intent(context, PlayerActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    data = uri
                }
                context.startActivity(intent)
            },
            onPlayVideos = { uris ->
                val intent = Intent(context, PlayerActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    data = uris.first()
                    putParcelableArrayListExtra(PlayerApi.API_PLAYLIST, ArrayList(uris))
                }
                context.startActivity(intent)
            },
            onFolderClick = navController::navigateToMediaPickerScreen,
            onSettingsClick = navController::navigateToSettings,
            onSearchClick = navController::navigateToSearch,
            onAddToPlaylistClick = navController::navigateToPlaylist,
        )

        searchScreen(
            onNavigateUp = navController::navigateUp,
/*
            onPlayVideo = { uri ->
                val intent = Intent(context, PlayerActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    data = uri
                }
                context.startActivity(intent)
            },
*/
            onPlayVideo = { videos, index ->
                val uris = ArrayList(videos.map { android.net.Uri.parse(it.uriString) })
                val intent = Intent(context, PlayerActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    data = uris[index]
                    putParcelableArrayListExtra(PlayerApi.API_PLAYLIST, uris)
                }
                context.startActivity(intent)
            },
            onPlayVideos = { uris ->
                if (uris.isNotEmpty()) {
                    val intent = Intent(context, PlayerActivity::class.java).apply {
                        action = Intent.ACTION_VIEW
                        data = uris.first()
                        putParcelableArrayListExtra(PlayerApi.API_PLAYLIST, ArrayList(uris))
                    }
                    context.startActivity(intent)
                }
            },
            onFolderClick = navController::navigateToMediaPickerScreen,
            onAddToPlaylistClick = { uris ->
                navController.navigateToPlaylist(uris)
            },
        )

        playlistScreen(
            navController = navController,
            onPlaylistClick = { playlistId, _ ->
                navController.navigateToPlaylistDetail(playlistId)
            },
            onBackClick = navController::popBackStack,
        )

        playlistDetailScreen(
            onPlayClick = { playlistId, uris, startIndex ->
                val androidUris = ArrayList(uris.map { android.net.Uri.parse(it) })
                val intent = Intent(context, PlayerActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    data = androidUris[startIndex]
                    putParcelableArrayListExtra(PlayerApi.API_PLAYLIST, androidUris)
                    putExtra("EXTRA_PLAYLIST_ID", playlistId)
                }
                context.startActivity(intent)
            },
            onVideoClick = { playlistId, uris, index ->
                val androidUris = ArrayList(uris.map { android.net.Uri.parse(it) })
                val intent = Intent(context, PlayerActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    data = androidUris[index]
                    putParcelableArrayListExtra(PlayerApi.API_PLAYLIST, androidUris)
                    putExtra("EXTRA_PLAYLIST_ID", playlistId)
                }
                context.startActivity(intent)
            },
            onBackClick = navController::popBackStack,
        )

        webdavNavGraph(
            navController = navController,
            onPlayFile = { urls, index, server ->
                val basicAuth = Credentials.basic(server.username, server.password)
                PlayerService.setWebdavCredentials(
                    host = server.host,
                    auth = basicAuth,
                )

                val uris = ArrayList(urls.map { android.net.Uri.parse(it) })
                val intent = Intent(context, PlayerActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    data = uris[index]
                    putParcelableArrayListExtra(PlayerApi.API_PLAYLIST, uris)
                }
                context.startActivity(intent)
            },
        )
    }
}
