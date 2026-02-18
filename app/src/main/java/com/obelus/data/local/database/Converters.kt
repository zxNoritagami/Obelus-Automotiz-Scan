package com.obelus.data.local.database

import androidx.room.TypeConverter
import com.obelus.data.local.model.Endian
import com.obelus.data.local.model.FileType
import com.obelus.data.local.model.SignalSource
import java.util.Date

class Converters {
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }

    @TypeConverter
    fun fromEndian(value: Endian): String {
        return value.name
    }

    @TypeConverter
    fun toEndian(value: String): Endian {
        return Endian.valueOf(value)
    }

    @TypeConverter
    fun fromSignalSource(value: SignalSource): String {
        return value.name
    }

    @TypeConverter
    fun toSignalSource(value: String): SignalSource {
        return SignalSource.valueOf(value)
    }

    @TypeConverter
    fun fromFileType(value: FileType): String {
        return value.name
    }

    @TypeConverter
    fun toFileType(value: String): FileType {
        return FileType.valueOf(value)
    }
}
