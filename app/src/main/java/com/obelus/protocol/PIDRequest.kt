package com.obelus.protocol

data class PIDRequest(
    val mode: String,
    val pid: String,
    val expectedBytes: Int? = null
) {
    fun toCommand(): String {
        return "$mode$pid"
    }
}
