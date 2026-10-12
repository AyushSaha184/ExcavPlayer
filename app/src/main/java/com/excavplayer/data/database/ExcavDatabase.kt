package com.excavplayer.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.excavplayer.data.database.dao.EqualizerPreferenceDao
import com.excavplayer.data.database.dao.MediaSourceDao
import com.excavplayer.data.database.dao.PlaybackDao
import com.excavplayer.data.database.dao.SubtitlePreferenceDao
import com.excavplayer.data.database.dao.VideoDao
import com.excavplayer.data.database.entity.DismissedContinueWatchingEntity
import com.excavplayer.data.database.entity.EqualizerPreferenceEntity
import com.excavplayer.data.database.entity.FolderEntity
import com.excavplayer.data.database.entity.MediaSourceEntity
import com.excavplayer.data.database.entity.PlaybackEntity
import com.excavplayer.data.database.entity.SubtitlePreferenceEntity
import com.excavplayer.data.database.entity.VideoEntity

@Database(
    entities = [
        VideoEntity::class,
        PlaybackEntity::class,
        SubtitlePreferenceEntity::class,
        MediaSourceEntity::class,
        FolderEntity::class,
        DismissedContinueWatchingEntity::class,
        EqualizerPreferenceEntity::class
    ],
    version = 6,
    exportSchema = true
)
abstract class ExcavDatabase : RoomDatabase() {

    abstract fun videoDao(): VideoDao
    abstract fun playbackDao(): PlaybackDao
    abstract fun mediaSourceDao(): MediaSourceDao
    abstract fun subtitlePreferenceDao(): SubtitlePreferenceDao
    abstract fun equalizerPreferenceDao(): EqualizerPreferenceDao

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

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_videos_availability_date_added` ON `videos` (`availability`, `date_added`)")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS favorites")
                db.execSQL("DROP TABLE IF EXISTS playlist_items")
                db.execSQL("DROP TABLE IF EXISTS playlists")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `equalizer_preferences` (
                        `video_id` TEXT NOT NULL,
                        `enabled` INTEGER NOT NULL,
                        `preset` TEXT NOT NULL,
                        `band_levels` TEXT NOT NULL,
                        `bass_boost_strength` INTEGER NOT NULL,
                        PRIMARY KEY(`video_id`)
                    )
                    """.trimIndent()
                )
            }
        }

        fun buildDatabase(context: Context): ExcavDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                ExcavDatabase::class.java,
                DATABASE_NAME
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}
