package com.easyhooon.dari.data.local

import androidx.room.TypeConverter
import com.easyhooon.dari.MessageDirection
import com.easyhooon.dari.MessageStatus

internal class Converters {
    @TypeConverter
    fun fromDirection(direction: MessageDirection): String = direction.name

    @TypeConverter
    fun toDirection(value: String): MessageDirection = MessageDirection.valueOf(value)

    @TypeConverter
    fun fromStatus(status: MessageStatus): String = status.name

    @TypeConverter
    fun toStatus(value: String): MessageStatus = MessageStatus.valueOf(value)
}
