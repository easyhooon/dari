package com.easyhooon.dari.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.easyhooon.dari.MessageStatus

@Dao
internal interface MessageDao {

    @Query("SELECT * FROM messages ORDER BY requestTimestamp ASC")
    suspend fun getAll(): List<MessageEntity>

    @Insert
    suspend fun insert(entity: MessageEntity): Long

    @Query(
        "UPDATE messages SET responseData = :responseData, status = :status, responseTimestamp = :responseTimestamp WHERE requestId = :requestId AND (tag = :tag OR (:tag IS NULL AND tag IS NULL))"
    )
    suspend fun updateByRequestId(
        requestId: String,
        tag: String?,
        responseData: String?,
        status: MessageStatus,
        responseTimestamp: Long?,
    )

    @Query(
        "DELETE FROM messages WHERE id NOT IN (SELECT id FROM messages ORDER BY requestTimestamp DESC LIMIT :maxEntries)"
    )
    suspend fun trimOldEntries(maxEntries: Int)

    @Query("DELETE FROM messages")
    suspend fun clear()
}