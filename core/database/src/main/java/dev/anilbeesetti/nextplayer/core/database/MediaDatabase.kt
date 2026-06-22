package dev.anilbeesetti.nextplayer.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dev.anilbeesetti.nextplayer.core.database.dao.MediumStateDao
import dev.anilbeesetti.nextplayer.core.database.dao.PlaylistDao
import dev.anilbeesetti.nextplayer.core.database.dao.WebdavServerDao
import dev.anilbeesetti.nextplayer.core.database.entities.MediumStateEntity
import dev.anilbeesetti.nextplayer.core.database.entities.PlaylistEntity
import dev.anilbeesetti.nextplayer.core.database.entities.PlaylistMediumCrossEntity
import dev.anilbeesetti.nextplayer.core.database.entities.WebdavServerEntity

@Database(
    entities = [
        MediumStateEntity::class,
        WebdavServerEntity::class,
        PlaylistEntity::class,
        PlaylistMediumCrossEntity::class,
    ],
    //version = 5,
    version = 10,
    exportSchema = true,
)
abstract class MediaDatabase : RoomDatabase() {

    abstract fun mediumStateDao(): MediumStateDao

    abstract fun webdavServerDao(): WebdavServerDao

    abstract fun playlistDao(): PlaylistDao

    companion object {
        const val DATABASE_NAME = "media_db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create the new media_state table
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `media_state` (
                        `uri` TEXT NOT NULL, 
                        `playback_position` INTEGER NOT NULL DEFAULT 0, 
                        `audio_track_index` INTEGER, 
                        `subtitle_track_index` INTEGER, 
                        `playback_speed` REAL, 
                        `last_played_time` INTEGER, 
                        `external_subs` TEXT NOT NULL DEFAULT '', 
                        `video_scale` REAL NOT NULL DEFAULT 1, 
                        PRIMARY KEY(`uri`)
                    )
                    """,
                )

                // Create index for the uri column
                db.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS `index_media_state_uri` ON `media_state` (`uri`)
                    """,
                )

                // Copy data from media table to media_state table
                db.execSQL(
                    """
                    INSERT INTO `media_state` (
                        `uri`, 
                        `playback_position`, 
                        `audio_track_index`, 
                        `subtitle_track_index`, 
                        `playback_speed`, 
                        `last_played_time`, 
                        `external_subs`, 
                        `video_scale`
                    ) 
                    SELECT 
                        `uri`, 
                        `playback_position`, 
                        `audio_track_index`, 
                        `subtitle_track_index`, 
                        `playback_speed`, 
                        `last_played_time`, 
                        `external_subs`, 
                        `video_scale` 
                    FROM `media`
                    """,
                )

                // Create a temporary table for the new media schema
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `media_new` (
                        `uri` TEXT NOT NULL, 
                        `path` TEXT NOT NULL, 
                        `filename` TEXT NOT NULL, 
                        `parent_path` TEXT NOT NULL, 
                        `last_modified` INTEGER NOT NULL, 
                        `size` INTEGER NOT NULL, 
                        `width` INTEGER NOT NULL, 
                        `height` INTEGER NOT NULL, 
                        `duration` INTEGER NOT NULL, 
                        `media_store_id` INTEGER NOT NULL, 
                        `format` TEXT, 
                        `thumbnail_path` TEXT, 
                        PRIMARY KEY(`uri`)
                    )
                    """,
                )

                // Copy data from the old media table to the new one
                db.execSQL(
                    """
                    INSERT INTO `media_new` (
                        `uri`, 
                        `path`, 
                        `filename`, 
                        `parent_path`, 
                        `last_modified`, 
                        `size`, 
                        `width`, 
                        `height`, 
                        `duration`, 
                        `media_store_id`, 
                        `format`, 
                        `thumbnail_path`
                    ) 
                    SELECT 
                        `uri`, 
                        `path`, 
                        `filename`, 
                        `parent_path`, 
                        `last_modified`, 
                        `size`, 
                        `width`, 
                        `height`, 
                        `duration`, 
                        `media_store_id`, 
                        `format`, 
                        `thumbnail_path` 
                    FROM `media`
                    """,
                )

                // Drop the old media table
                db.execSQL("DROP TABLE `media`")

                // Rename the new media table to media
                db.execSQL("ALTER TABLE `media_new` RENAME TO `media`")

                // Recreate the indices for the media table
                db.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS `index_media_uri` ON `media` (`uri`)
                    """,
                )
                db.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS `index_media_path` ON `media` (`path`)
                    """,
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Drop the unique index on path
                db.execSQL("DROP INDEX IF EXISTS `index_media_path`")

                // Recreate the index without unique constraint
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS `index_media_path` ON `media` (`path`)
                    """,
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `media_state` ADD COLUMN `subtitle_delay` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `media_state` ADD COLUMN `subtitle_speed` REAL NOT NULL DEFAULT 1")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `webdav_servers` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                        `name` TEXT NOT NULL, 
                        `host` TEXT NOT NULL, 
                        `port` INTEGER NOT NULL, 
                        `path` TEXT NOT NULL, 
                        `username` TEXT NOT NULL, 
                        `password` TEXT NOT NULL, 
                        `useSsl` INTEGER NOT NULL, 
                        `allowSelfSigned` INTEGER NOT NULL, 
                        `createdAt` INTEGER NOT NULL, 
                        `updatedAt` INTEGER NOT NULL
                    )
                """.trimIndent()
                )
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `media_state` ADD COLUMN `duration_ms` INTEGER"
                )
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
            CREATE TABLE IF NOT EXISTS `playlists` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `created_at` INTEGER NOT NULL,
                `updated_at` INTEGER NOT NULL,
                `last_played_uri` TEXT DEFAULT NULL
            )
            """.trimIndent()
                )

                db.execSQL(
                    """
            CREATE UNIQUE INDEX IF NOT EXISTS `index_playlists_name`
            ON `playlists` (`name`)
            """.trimIndent()
                )

                db.execSQL(
                    """
            CREATE TABLE IF NOT EXISTS `playlist_medium_cross_entity` (
                `playlist_id` INTEGER NOT NULL,
                `medium_uri` TEXT NOT NULL,
                `position` INTEGER NOT NULL,
                `added_at` INTEGER NOT NULL,
                PRIMARY KEY(`playlist_id`, `medium_uri`),
                FOREIGN KEY(`playlist_id`) REFERENCES `playlists`(`id`) ON DELETE CASCADE,
                FOREIGN KEY(`medium_uri`) REFERENCES `media`(`uri`) ON DELETE CASCADE
            )
            """.trimIndent()
                )

                db.execSQL(
                    """
            CREATE INDEX IF NOT EXISTS `index_playlist_medium_cross_entity_playlist_id`
            ON `playlist_medium_cross_entity` (`playlist_id`)
            """.trimIndent()
                )

                db.execSQL(
                    """
            CREATE INDEX IF NOT EXISTS `index_playlist_medium_cross_entity_medium_uri`
            ON `playlist_medium_cross_entity` (`medium_uri`)
            """.trimIndent()
                )
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
            ALTER TABLE `playlists` ADD COLUMN `sort_option` TEXT NOT NULL DEFAULT 'ADDED_ASC'
            """.trimIndent()
                )

                db.execSQL(
                    """
            ALTER TABLE `playlists` ADD COLUMN `position` INTEGER NOT NULL DEFAULT 0
            """.trimIndent()
                )

                db.execSQL(
                    """
            ALTER TABLE `webdav_servers` ADD COLUMN `position` INTEGER NOT NULL DEFAULT 0
            """.trimIndent()
                )

                db.execSQL(
                    """
            CREATE TABLE IF NOT EXISTS `playlist_medium_cross_entity_backup` (
                `playlist_id` INTEGER NOT NULL,
                `medium_uri` TEXT NOT NULL,
                `position` INTEGER NOT NULL,
                `added_at` INTEGER NOT NULL,
                PRIMARY KEY(`playlist_id`, `medium_uri`),
                FOREIGN KEY(`playlist_id`) REFERENCES `playlists`(`id`) ON DELETE CASCADE
            )
            """.trimIndent()
                )

                db.execSQL(
                    """
            INSERT INTO `playlist_medium_cross_entity_backup`
            SELECT `playlist_id`, `medium_uri`, `position`, `added_at`
            FROM `playlist_medium_cross_entity`
            """.trimIndent()
                )

                db.execSQL("DROP TABLE `playlist_medium_cross_entity`")

                db.execSQL(
                    """
            CREATE TABLE IF NOT EXISTS `playlist_medium_cross_entity` (
                `playlist_id` INTEGER NOT NULL,
                `medium_uri` TEXT NOT NULL,
                `position` INTEGER NOT NULL,
                `added_at` INTEGER NOT NULL,
                `is_remote` INTEGER NOT NULL DEFAULT 0,
                `display_name` TEXT NOT NULL DEFAULT '',
                `full_url` TEXT NOT NULL DEFAULT '',
                PRIMARY KEY(`playlist_id`, `medium_uri`),
                FOREIGN KEY(`playlist_id`) REFERENCES `playlists`(`id`) ON DELETE CASCADE
            )
            """.trimIndent()
                )

                db.execSQL(
                    """
            INSERT INTO `playlist_medium_cross_entity`
            (`playlist_id`, `medium_uri`, `position`, `added_at`, `is_remote`, `display_name`, `full_url`)
            SELECT `playlist_id`, `medium_uri`, `position`, `added_at`, 0, '', `medium_uri`
            FROM `playlist_medium_cross_entity_backup`
            """.trimIndent()
                )

                db.execSQL("DROP TABLE `playlist_medium_cross_entity_backup`")

                db.execSQL(
                    """
            CREATE INDEX IF NOT EXISTS `index_playlist_medium_cross_entity_playlist_id`
            ON `playlist_medium_cross_entity` (`playlist_id`)
            """.trimIndent()
                )

                db.execSQL(
                    """
            CREATE INDEX IF NOT EXISTS `index_playlist_medium_cross_entity_medium_uri`
            ON `playlist_medium_cross_entity` (`medium_uri`)
            """.trimIndent()
                )
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
            CREATE TABLE IF NOT EXISTS `playlist_medium_cross_entity_new` (
                `playlist_id` INTEGER NOT NULL,
                `medium_uri` TEXT NOT NULL,
                `position` INTEGER NOT NULL,
                `added_at` INTEGER NOT NULL,
                `is_remote` INTEGER NOT NULL DEFAULT 0,
                `display_name` TEXT NOT NULL DEFAULT '',
                PRIMARY KEY(`playlist_id`, `medium_uri`),
                FOREIGN KEY(`playlist_id`) REFERENCES `playlists`(`id`) ON DELETE CASCADE
            )
        """.trimIndent())

                db.execSQL("""
            INSERT INTO `playlist_medium_cross_entity_new`
            (`playlist_id`, `medium_uri`, `position`, `added_at`, `is_remote`, `display_name`)
            SELECT `playlist_id`, `medium_uri`, `position`, `added_at`, `is_remote`, `display_name`
            FROM `playlist_medium_cross_entity`
        """.trimIndent())

                db.execSQL("DROP TABLE `playlist_medium_cross_entity`")

                db.execSQL("ALTER TABLE `playlist_medium_cross_entity_new` RENAME TO `playlist_medium_cross_entity`")

                db.execSQL("""
            CREATE INDEX IF NOT EXISTS `index_playlist_medium_cross_entity_playlist_id`
            ON `playlist_medium_cross_entity` (`playlist_id`)
        """.trimIndent())

                db.execSQL("""
            CREATE INDEX IF NOT EXISTS `index_playlist_medium_cross_entity_medium_uri`
            ON `playlist_medium_cross_entity` (`medium_uri`)
        """.trimIndent())
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS `directories`")
                db.execSQL("DROP TABLE IF EXISTS `media`")
                db.execSQL("DROP TABLE IF EXISTS `audio_stream_info`")
                db.execSQL("DROP TABLE IF EXISTS `video_stream_info`")
                db.execSQL("DROP TABLE IF EXISTS `subtitle_stream_info`")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `playlist_medium_cross_entity_new` (
                        `playlist_id` INTEGER NOT NULL,
                        `medium_uri` TEXT NOT NULL,
                        `position` INTEGER NOT NULL,
                        `added_at` INTEGER NOT NULL,
                        `is_remote` INTEGER NOT NULL DEFAULT 0,
                        `display_name` TEXT NOT NULL DEFAULT '',
                        `file_size` INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(`playlist_id`, `medium_uri`)
                    )
                """.trimIndent())

                db.execSQL("""
                    INSERT INTO `playlist_medium_cross_entity_new`
                    (`playlist_id`, `medium_uri`, `position`, `added_at`, `is_remote`, `display_name`, `file_size`)
                    SELECT `playlist_id`, `medium_uri`, `position`, `added_at`, `is_remote`, `display_name`, 0
                    FROM `playlist_medium_cross_entity`
                """.trimIndent())

                db.execSQL("DROP TABLE `playlist_medium_cross_entity`")
                db.execSQL("ALTER TABLE `playlist_medium_cross_entity_new` RENAME TO `playlist_medium_cross_entity`")

                db.execSQL("""
                    CREATE INDEX IF NOT EXISTS `index_playlist_medium_cross_entity_playlist_id`
                    ON `playlist_medium_cross_entity` (`playlist_id`)
                """.trimIndent())

                db.execSQL("""
                    CREATE INDEX IF NOT EXISTS `index_playlist_medium_cross_entity_medium_uri`
                    ON `playlist_medium_cross_entity` (`medium_uri`)
                """.trimIndent())
            }
        }
    }
}
