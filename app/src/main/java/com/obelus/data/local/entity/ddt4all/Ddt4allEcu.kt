package com.obelus.data.local.entity.ddt4all

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ddt4all_ecus")
data class Ddt4allEcu(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val protocol: String,
    val group: String,
    val originFile: String
) {
    @androidx.room.Ignore
    var parameters: List<com.obelus.data.ddt4all.Ddt4allParameter> = emptyList()
    @androidx.room.Ignore
    var commands: List<com.obelus.data.ddt4all.Ddt4allCommand> = emptyList()
}
