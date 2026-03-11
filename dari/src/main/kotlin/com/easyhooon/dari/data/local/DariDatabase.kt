package com.easyhooon.dari.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [MessageEntity::class],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
internal abstract class DariDatabase : RoomDatabase() {

    abstract fun messageDao(): MessageDao

    companion object {
        private const val DB_NAME = "dari.db"

        fun create(context: Context): DariDatabase {
            return Room.databaseBuilder(context, DariDatabase::class.java, DB_NAME)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
        }
    }
}