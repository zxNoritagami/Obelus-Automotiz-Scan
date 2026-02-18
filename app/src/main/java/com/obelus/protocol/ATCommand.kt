package com.obelus.protocol

object ATCommand {
    // Basic
    const val RESET = "ATZ"
    const val ECHO_OFF = "ATE0"
    const val LINE_FEED_OFF = "ATL0"
    const val HEADERS_ON = "ATH1"
    const val SPACES_OFF = "ATS0"
    const val PRINT_VERSION = "ATI"
    
    // Configuration
    const val SET_PROTOCOL = "ATSP" // + id
    const val SET_HEADER = "ATSH"   // + header
    const val SET_RECEIVE_FILTER = "ATCRA" // + mask
    const val SET_TIMEOUT = "ATST"  // + value
    const val ALLOW_LONG_MESSAGES = "ATAL"
    const val ADAPTIVE_TIMING_AUTO_1 = "ATAT1"
    
    // ISO-TP
    const val ISO_TP_FORMAT_ON = "ATCAF1"
    
    // Queries
    const val DESCRIBE_PROTOCOL = "ATDP"
    const val DESCRIBE_PROTOCOL_NUM = "ATDPN"
    const val READ_VOLTAGE = "ATRV"
}
