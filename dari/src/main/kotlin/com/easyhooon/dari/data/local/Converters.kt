package com.easyhooon.dari.data.local

import androidx.room.TypeConverter
import com.easyhooon.dari.MessageDirection
import com.easyhooon.dari.MessageStatus
import com.easyhooon.dari.PayloadContentType
import com.easyhooon.dari.PayloadDecodeStatus

internal class Converters {
    @TypeConverter
    fun fromDirection(direction: MessageDirection): String = direction.name

    @TypeConverter
    fun toDirection(value: String): MessageDirection = MessageDirection.valueOf(value)

    @TypeConverter
    fun fromStatus(status: MessageStatus): String = status.name

    @TypeConverter
    fun toStatus(value: String): MessageStatus = MessageStatus.valueOf(value)

    @TypeConverter
    fun fromPayloadContentType(contentType: PayloadContentType): String = contentType.name

    @TypeConverter
    fun toPayloadContentType(value: String): PayloadContentType = PayloadContentType.valueOf(value)

    @TypeConverter
    fun fromPayloadDecodeStatus(status: PayloadDecodeStatus): String = status.name

    @TypeConverter
    fun toPayloadDecodeStatus(value: String): PayloadDecodeStatus = PayloadDecodeStatus.valueOf(value)
}
