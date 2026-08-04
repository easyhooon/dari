package com.easyhooon.dari.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [MessageEntity::class],
    version = 4,
    exportSchema = false,
)
@TypeConverters(Converters::class)
internal abstract class DariDatabase : RoomDatabase() {

    abstract fun messageDao(): MessageDao

    companion object {
        private const val DB_NAME = "dari.db"

        fun create(context: Context): DariDatabase {
            return Room.databaseBuilder(context, DariDatabase::class.java, DB_NAME)
                .addMigrations(MIGRATION_3_4)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `messages` ADD COLUMN `requestContentType` TEXT")
                database.execSQL("ALTER TABLE `messages` ADD COLUMN `requestOriginalSizeBytes` INTEGER")
                database.execSQL("ALTER TABLE `messages` ADD COLUMN `requestDecodeStatus` TEXT")
                database.execSQL("ALTER TABLE `messages` ADD COLUMN `responseContentType` TEXT")
                database.execSQL("ALTER TABLE `messages` ADD COLUMN `responseOriginalSizeBytes` INTEGER")
                database.execSQL("ALTER TABLE `messages` ADD COLUMN `responseDecodeStatus` TEXT")
            }
        }
    }
}
