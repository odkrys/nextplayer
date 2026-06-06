package dev.anilbeesetti.nextplayer

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File
import javax.inject.Singleton

@OptIn(UnstableApi::class)
@Module
@InstallIn(SingletonComponent::class)
object DiskCacheModule {


    @Provides
    @Singleton
    fun provideSimpleCache(
        @ApplicationContext context: Context,
        preferencesRepository: PreferencesRepository
    ): SimpleCache {

        val cacheSizeMb = runBlocking {
            preferencesRepository.applicationPreferences.first().diskCacheSizeMb
        }

        val cacheDir = File(context.cacheDir, "media_cache")

        if (cacheSizeMb == 0) {
            cacheDir.deleteRecursively()
        }

        val cacheSizeBytes = cacheSizeMb * 1024 * 1024L

        return SimpleCache(
            cacheDir,
            LeastRecentlyUsedCacheEvictor(cacheSizeBytes),
            StandaloneDatabaseProvider(context)
        )
    }

    @Provides
    @Singleton
    fun provideCacheDataSourceFactory(simpleCache: SimpleCache): CacheDataSource.Factory {
        return CacheDataSource.Factory()
            .setCache(simpleCache)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }
}
