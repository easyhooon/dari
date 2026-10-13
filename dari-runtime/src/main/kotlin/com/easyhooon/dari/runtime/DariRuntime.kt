package com.easyhooon.dari.runtime

import android.content.Context
import com.easyhooon.dari.DariConfig
import com.easyhooon.dari.data.MessageRepository
import com.easyhooon.dari.data.local.DariDatabase

/** Capture and storage shared by the in-app (`dari`) and desktop (`dari-desktop`) artifacts. */
@InternalDariApi
object DariRuntime {

    var config = DariConfig()
        private set

    lateinit var repository: MessageRepository
        private set

    private var database: DariDatabase? = null

    @Synchronized
    fun init(context: Context, config: DariConfig) {
        this.config = config
        if (database == null) {
            val db = DariDatabase.create(context.applicationContext)
            database = db
            repository = MessageRepository(
                database = db,
                maxEntries = config.maxEntries,
                retentionPeriodMs = config.retentionPeriod?.inWholeMilliseconds,
            )
        }
    }
}
