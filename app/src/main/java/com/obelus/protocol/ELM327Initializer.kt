package com.obelus.protocol

class ELM327Initializer(private val connection: ElmConnection) {

    suspend fun initialize(protocol: OBD2Protocol = OBD2Protocol.AUTO): Boolean {
        println("ELM327Initializer: Initializing with protocol ${protocol.name}")
        
        try {
            sendOrThrow(ATCommand.RESET)          // ATZ
            sendOrThrow(ATCommand.ECHO_OFF)       // ATE0
            sendOrThrow(ATCommand.LINE_FEED_OFF)  // ATL0
            sendOrThrow(ATCommand.HEADERS_ON)     // ATH1
            sendOrThrow(ATCommand.SPACES_OFF)     // ATS0
            sendOrThrow(ATCommand.ISO_TP_FORMAT_ON) // ATCAF1
            
            // Set Protocol
            val setProtoCmd = ATCommand.SET_PROTOCOL + protocol.id
            connection.send(setProtoCmd)
            println("ELM327Initializer: Protocol set command sent: $setProtoCmd")

            return true
        } catch (e: Exception) {
            println("ELM327Initializer: Initialization failed: ${e.message}")
            return false
        }
    }

    private suspend fun sendOrThrow(cmd: String) {
        val response = connection.send(cmd)
        println("ELM327Initializer: CMD: $cmd -> RESP: $response")
        // In strict mode, we might check for "OK" or similar, but ELM responses vary
    }
}
