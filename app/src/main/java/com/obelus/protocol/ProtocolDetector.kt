package com.obelus.protocol

class ProtocolDetector(private val connection: ElmConnection) {

    private var currentProtocol: OBD2Protocol? = null

    suspend fun autoDetect(): OBD2Protocol {
        println("ProtocolDetector: Starting auto-detection...")
        
        // Reset and Defaults
        connection.send(ATCommand.RESET)
        connection.send(ATCommand.ECHO_OFF)
        connection.send(ATCommand.SPACES_OFF)
        
        // Try Auto
        connection.send(ATCommand.SET_PROTOCOL + "0") 
        val response = connection.send("0100") // PID 01 00 to trigger search
        
        println("ProtocolDetector: Auto detect response: $response")
        
        if (response.contains("SEARCHING", ignoreCase = true)) {
             // It might take time, or we might need to check "ATDPN"
        }

        // Check what protocol was selected
        val dpn = connection.send(ATCommand.DESCRIBE_PROTOCOL_NUM)
        println("ProtocolDetector: ATDPN response: $dpn")
        
        val protocolId = dpn.replace("A", "").trim().toIntOrNull() ?: 0
        val detected = OBD2Protocol.fromId(protocolId)
        
        currentProtocol = detected
        println("ProtocolDetector: Detected protocol: ${detected.name}")
        
        return detected
    }

    suspend fun tryProtocol(protocol: OBD2Protocol): Boolean {
        println("ProtocolDetector: Trying protocol ${protocol.name} (ID: ${protocol.id})...")
        connection.send(ATCommand.SET_PROTOCOL + protocol.id)
        val response = connection.send("0100")
        
        val success = !response.contains("UNABLE TO CONNECT") && 
                      !response.contains("BUS INIT: ...ERROR") &&
                      !response.contains("NO DATA")
                      
        if (success) {
            currentProtocol = protocol
            println("ProtocolDetector: Connected with ${protocol.name}")
        } else {
            println("ProtocolDetector: Failed to connect with ${protocol.name}")
        }
        
        return success
    }

    fun getCurrentProtocol(): OBD2Protocol? = currentProtocol
}
