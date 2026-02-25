package com.obelus.data.protocol.wifi

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton

data class DiscoveredDevice(
    val ip: String,
    val port: Int,
    val rtt: Long
)

@Singleton
class WifiDiscovery @Inject constructor() {
    
    companion object {
        private const val BASE_IP = "192.168.0"
        private const val START_IP = 10
        private const val END_IP = 50
        private const val TIMEOUT_MS = 500
        private val PORTS = listOf(35000, 23)
    }

    suspend fun scanNetwork(): List<DiscoveredDevice> = withContext(Dispatchers.IO) {
        val tasks = mutableListOf<kotlinx.coroutines.Deferred<DiscoveredDevice?>>()

        for (i in START_IP..END_IP) {
            val ip = "$BASE_IP.$i"
            for (port in PORTS) {
                tasks.add(async { checkPort(ip, port) })
            }
        }

        tasks.awaitAll().filterNotNull()
    }

    private fun checkPort(ip: String, port: Int): DiscoveredDevice? {
        val startTime = System.currentTimeMillis()
        var socket: Socket? = null
        try {
            socket = Socket()
            socket.connect(InetSocketAddress(ip, port), TIMEOUT_MS)
            val rtt = System.currentTimeMillis() - startTime
            return DiscoveredDevice(ip, port, rtt)
        } catch (e: Exception) {
            // Ignore timeout or connection errors
            return null
        } finally {
            try {
                socket?.close()
            } catch (e: Exception) {
                // Ignore close error
            }
        }
    }
}
