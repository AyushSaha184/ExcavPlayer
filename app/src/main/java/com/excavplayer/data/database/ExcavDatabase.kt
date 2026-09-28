package com.excavplayer.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.excavplayer.data.database.dao.FavoriteDao
import com.excavplayer.data.database.dao.HistoryDao
import com.excavplayer.data.database.dao.MediaSourceDao
import com.excavplayer.data.database.dao.PlaybackDao
import com.excavplayer.data.database.dao.PlaylistDao
import com.excavplayer.data.database.dao.SubtitlePreferenceDao
import com.excavplayer.data.database.dao.VideoDao
import com.excavplayer.data.database.entity.FavoriteEntity
import com.excavplayer.data.database.entity.FolderEntity
import com.excavplayer.data.database.entity.MediaSourceEntity
import com.excavplayer.data.database.entity.PlaybackEntity
import com.excavplayer.data.database.entity.PlaylistEntity
import com.excavplayer.data.database.entity.PlaylistItemEntity
import com.excavplayer.data.database.entity.SubtitlePreferenceEntity
import com.excavplayer.data.database.entity.VideoEntity
import com.excavplayer.data.database.entity.WatchHistoryEntity

@Database(
    entities = [
        VideoEntity::class,
        PlaybackEntity::class,
        WatchHistoryEntity::class,
        FavoriteEntity::class,
        PlaylistEntity::class,
        PlaylistItemEntity::class,
        SubtitlePreferenceEntity::class,
        MediaSourceEntity::class,
        FolderEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class ExcavDatabase : RoomDatabase() {

    abstract fun videoDao(): VideoDao
    abstract fun playbackDao(): PlaybackDao
    abstract fun historyDao(): HistoryDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun mediaSourceDao(): MediaSourceDao
    abstract fun subtitlePreferenceDao(): SubtitlePreferenceDao

    companion object {
        const val DATABASE_NAME = "excav_player.db"

        // Example migration definition for future schema evolution
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Migration hook for future schema adjustments
            }
        }

        fun buildDatabase(context: Context): ExcavDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                ExcavDatabase::class.java,
                DATABASE_NAME
            )
                .addMigrations(MIGRATION_1_2)
                .build()
        }
    }
}
