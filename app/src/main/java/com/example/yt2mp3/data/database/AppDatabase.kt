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

@Database(entities = [DownloadEntity::class], version = 3, exportSchema = false)
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

        @Volatile private var instance: AppDatabase? = null
        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "yt2mp3.db",
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
        }
    }
}
