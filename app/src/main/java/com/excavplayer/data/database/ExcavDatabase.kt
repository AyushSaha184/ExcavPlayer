package com.excavplayer.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.excavplayer.data.database.dao.FavoriteDao
import com.excavplayer.data.database.dao.MediaSourceDao
import com.excavplayer.data.database.dao.PlaybackDao
import com.excavplayer.data.database.dao.PlaylistDao
import com.excavplayer.data.database.dao.SubtitlePreferenceDao
import com.excavplayer.data.database.dao.VideoDao
import com.excavplayer.data.database.entity.DismissedContinueWatchingEntity
import com.excavplayer.data.database.entity.FavoriteEntity
import com.excavplayer.data.database.entity.FolderEntity
import com.excavplayer.data.database.entity.MediaSourceEntity
import com.excavplayer.data.database.entity.PlaybackEntity
import com.excavplayer.data.database.entity.PlaylistEntity
import com.excavplayer.data.database.entity.PlaylistItemEntity
import com.excavplayer.data.database.entity.SubtitlePreferenceEntity
import com.excavplayer.data.database.entity.VideoEntity

@Database(
    entities = [
        VideoEntity::class,
        PlaybackEntity::class,
        FavoriteEntity::class,
        PlaylistEntity::class,
        PlaylistItemEntity::class,
        SubtitlePreferenceEntity::class,
        MediaSourceEntity::class,
        FolderEntity::class,
        DismissedContinueWatchingEntity::class
    ],
    version = 3,
    exportSchema = true
)
abstract class ExcavDatabase : RoomDatabase() {

    abstract fun videoDao(): VideoDao
    abstract fun playbackDao(): PlaybackDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun mediaSourceDao(): MediaSourceDao
    abstract fun subtitlePreferenceDao(): SubtitlePreferenceDao

    companion object {
        const val DATABASE_NAME = "excav_player.db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS watch_history")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `dismissed_continue_watching` (`video_id` TEXT NOT NULL, PRIMARY KEY(`video_id`), FOREIGN KEY(`video_id`) REFERENCES `videos`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
            }
        }

        fun buildDatabase(context: Context): ExcavDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                ExcavDatabase::class.java,
                DATABASE_NAME
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}
