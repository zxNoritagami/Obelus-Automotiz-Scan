package com.obelus.protocol

interface ElmConnection {
    suspend fun send(command: String): String
}
