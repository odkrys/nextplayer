package dev.anilbeesetti.nextplayer.core.domain

import dev.anilbeesetti.nextplayer.core.common.Dispatcher
import dev.anilbeesetti.nextplayer.core.common.NextDispatchers
import dev.anilbeesetti.nextplayer.core.data.repository.MediaRepository
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.model.Folder
import dev.anilbeesetti.nextplayer.core.model.Sort
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn

class GetSortedFoldersUseCase @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val preferencesRepository: PreferencesRepository,
    @Dispatcher(NextDispatchers.Default) private val defaultDispatcher: CoroutineDispatcher,
) {

    operator fun invoke(folderPath: String? = null): Flow<List<Folder>> {
        return combine(
            mediaRepository.observeFolders(folderPath),
            mediaRepository.observeVideos(folderPath),
            preferencesRepository.applicationPreferences,
        //) { folders, preferences ->
        ) { folders, videos, preferences ->

            val updatedFolders = folders.map { folder ->
                val videosInFolder = videos.filter { it.parentPath == folder.path }

                if (videosInFolder.isNotEmpty()) {
                    folder.copy(
                        totalDuration = videosInFolder.sumOf { it.duration },
                        videosCount = videosInFolder.size,
                        totalSize = videosInFolder.sumOf { it.size }
                    )
                } else {
                    folder
                }
            }
/*
            val nonExcludedDirectories = folders.filter {
                it.path !in preferences.excludeFolders
            }
*/
            val nonExcludedDirectories = updatedFolders.filter {
                it.path !in preferences.excludeFolders
            }

            val sort = Sort(by = preferences.sortBy, order = preferences.sortOrder)
            nonExcludedDirectories.sortedWith(sort.folderComparator())
        }.flowOn(defaultDispatcher)
    }
}
