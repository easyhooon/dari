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
    version = 6,
    exportSchema = false,
)
@TypeConverters(Converters::class)
internal abstract class DariDatabase : RoomDatabase() {

    abstract fun messageDao(): MessageDao

    companion object {
        private const val DB_NAME = "dari.db"

        fun create(context: Context): DariDatabase {
            return Room.databaseBuilder(context, DariDatabase::class.java, DB_NAME)
                .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `messages` ADD COLUMN `requestContentType` TEXT")
                db.execSQL("ALTER TABLE `messages` ADD COLUMN `requestOriginalSizeBytes` INTEGER")
                db.execSQL("ALTER TABLE `messages` ADD COLUMN `requestDecodeStatus` TEXT")
                db.execSQL("ALTER TABLE `messages` ADD COLUMN `responseContentType` TEXT")
                db.execSQL("ALTER TABLE `messages` ADD COLUMN `responseOriginalSizeBytes` INTEGER")
                db.execSQL("ALTER TABLE `messages` ADD COLUMN `responseDecodeStatus` TEXT")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `messages` ADD COLUMN `requestRawPreviewBase64` TEXT")
                db.execSQL("ALTER TABLE `messages` ADD COLUMN `requestRawPreviewSizeBytes` INTEGER")
                db.execSQL("ALTER TABLE `messages` ADD COLUMN `requestRawPreviewTruncated` INTEGER")
                db.execSQL("ALTER TABLE `messages` ADD COLUMN `responseRawPreviewBase64` TEXT")
                db.execSQL("ALTER TABLE `messages` ADD COLUMN `responseRawPreviewSizeBytes` INTEGER")
                db.execSQL("ALTER TABLE `messages` ADD COLUMN `responseRawPreviewTruncated` INTEGER")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `messages` ADD COLUMN `displayName` TEXT")
            }
        }
    }
}
