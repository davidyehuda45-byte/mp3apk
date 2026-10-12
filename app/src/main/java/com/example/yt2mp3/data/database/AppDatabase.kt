package com.example.yt2mp3.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.yt2mp3.domain.model.DownloadStatus

class DownloadStatusConverter {
    @TypeConverter fun toString(s: DownloadStatus): String = s.name
    @TypeConverter fun toStatus(s: String): DownloadStatus =
        runCatching { DownloadStatus.valueOf(s) }.getOrDefault(DownloadStatus.FAILED)
}

@Database(entities = [DownloadEntity::class], version = 4, exportSchema = false)
@TypeConverters(DownloadStatusConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun downloadDao(): DownloadDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE downloads ADD COLUMN outputUri TEXT")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE downloads ADD COLUMN format TEXT NOT NULL DEFAULT 'MP3'")
                db.execSQL("ALTER TABLE downloads ADD COLUMN sectionStart INTEGER")
                db.execSQL("ALTER TABLE downloads ADD COLUMN sectionEnd INTEGER")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE downloads ADD COLUMN mediaKind TEXT NOT NULL DEFAULT 'AUDIO'")
                db.execSQL("ALTER TABLE downloads ADD COLUMN videoFormat TEXT")
                db.execSQL("ALTER TABLE downloads ADD COLUMN videoQuality TEXT")
                db.execSQL("ALTER TABLE downloads ADD COLUMN subtitleMode TEXT NOT NULL DEFAULT 'NONE'")
                db.execSQL("ALTER TABLE downloads ADD COLUMN subLangs TEXT NOT NULL DEFAULT 'en,id'")
                db.execSQL("ALTER TABLE downloads ADD COLUMN embedChapters INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE downloads ADD COLUMN sponsorBlock INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE downloads ADD COLUMN normalizeAudio INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE downloads ADD COLUMN saveThumb INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE downloads ADD COLUMN stripMetadata INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE downloads ADD COLUMN subfolder TEXT")
                db.execSQL("ALTER TABLE downloads ADD COLUMN favorite INTEGER NOT NULL DEFAULT 0")
            }
        }

        @Volatile private var instance: AppDatabase? = null
        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "yt2mp3.db",
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build().also { instance = it }
        }
    }
}
