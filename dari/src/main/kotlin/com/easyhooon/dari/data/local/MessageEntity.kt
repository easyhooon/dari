package com.easyhooon.dari.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.easyhooon.dari.MessageDirection
import com.easyhooon.dari.MessageEntry
import com.easyhooon.dari.MessageStatus

@Entity(tableName = "messages")
internal data class MessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val requestId: String?,
    val handlerName: String,
    val direction: MessageDirection,
    val tag: String? = null,
    val requestData: String? = null,
    val responseData: String? = null,
    val status: MessageStatus = MessageStatus.IN_PROGRESS,
    val requestTimestamp: Long = System.currentTimeMillis(),
    val responseTimestamp: Long? = null,
)

internal fun MessageEntry.toEntity(): MessageEntity = MessageEntity(
    requestId = requestId,
    handlerName = handlerName,
    direction = direction,
    requestData = requestData,
    responseData = responseData,
    status = status,
    requestTimestamp = requestTimestamp,
    responseTimestamp = responseTimestamp,
)

internal fun MessageEntity.toMessageEntry(): MessageEntry = MessageEntry(
    requestId = requestId ?: "",
    handlerName = handlerName,
    direction = direction,
    requestData = requestData,
    responseData = responseData,
    status = status,
    requestTimestamp = requestTimestamp,
    responseTimestamp = responseTimestamp,
)