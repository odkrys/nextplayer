package dev.anilbeesetti.nextplayer.core.database

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.anilbeesetti.nextplayer.core.database.dao.DirectoryDao
import dev.anilbeesetti.nextplayer.core.database.dao.MediumDao
import dev.anilbeesetti.nextplayer.core.database.dao.PlaylistDao
import dev.anilbeesetti.nextplayer.core.database.dao.WebdavServerDao

@Module
@InstallIn(SingletonComponent::class)
object DaoModule {

    @Provides
    fun provideMediumDao(db: MediaDatabase): MediumDao = db.mediumDao()

    @Provides
    fun provideMediumStateDao(db: MediaDatabase) = db.mediumStateDao()

    @Provides
    fun provideDirectoryDao(db: MediaDatabase): DirectoryDao = db.directoryDao()

    @Provides
    fun provideWebdavServerDao(db: MediaDatabase): WebdavServerDao = db.webdavServerDao()

    @Provides
    fun providePlaylistDao(db: MediaDatabase): PlaylistDao = db.playlistDao()
}
