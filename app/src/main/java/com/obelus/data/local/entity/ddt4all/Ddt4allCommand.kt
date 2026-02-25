package com.obelus.data.local.entity.ddt4all

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "ddt4all_commands",
    foreignKeys = [
        ForeignKey(
            entity = Ddt4allEcu::class,
            parentColumns = ["id"],
            childColumns = ["ecuId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["ecuId"])]
)
data class Ddt4allCommand(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "ecuId")
    val ecuId: Long,
    val name: String,
    val request: String,
    val responseLength: Int?
)
