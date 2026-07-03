package dev.anilbeesetti.nextplayer.core.model

import kotlinx.serialization.Serializable

@Serializable
data class ApplicationPreferences(
    val sortBy: Sort.By = Sort.By.TITLE,
    val sortOrder: Sort.Order = Sort.Order.ASCENDING,
    val themeConfig: ThemeConfig = ThemeConfig.SYSTEM,
    val useHighContrastDarkTheme: Boolean = false,
    val useDynamicColors: Boolean = true,
    val markLastPlayedMedia: Boolean = true,
    val scrollToLastPlayedMedia: Boolean = false,
    val excludeFolders: List<String> = emptyList(),
    val hideExcludedMediaInPlaylists: Boolean = false,
    val mediaViewMode: MediaViewMode = MediaViewMode.FOLDERS,
    val mediaLayoutMode: MediaLayoutMode = MediaLayoutMode.LIST,
    val webdavShowOnlyPlayable: Boolean = false,

    // Fields
    val showDurationField: Boolean = true,
    val showExtensionField: Boolean = false,
    val showPathField: Boolean = true,
    val showResolutionField: Boolean = false,
    val showSizeField: Boolean = false,
    val showThumbnailField: Boolean = true,
    val showPlayedProgress: Boolean = true,

    // Thumbnail generation
    val thumbnailGenerationStrategy: ThumbnailGenerationStrategy = ThumbnailGenerationStrategy.HYBRID,
    val thumbnailFramePosition: Float = DEFAULT_THUMBNAIL_FRAME_POSITION,
    val webdavThumbnailMode: WebdavThumbnailMode = WebdavThumbnailMode.OFF,

    // Disk Cache
    val diskCacheSizeMb: Int = 512,
) {

    companion object {
        const val DEFAULT_THUMBNAIL_FRAME_POSITION = 0.33f
    }
}
