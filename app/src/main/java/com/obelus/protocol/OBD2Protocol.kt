package com.obelus.protocol

enum class OBD2Protocol(val id: Int, val protocolName: String, val description: String) {
    AUTO(0, "Auto", "Automatic Protocol Detection"),
    J1850_PWM(1, "SAE J1850 PWM", "41.6 kbaud"),
    J1850_VPW(2, "SAE J1850 VPW", "10.4 kbaud"),
    ISO9141_2(3, "ISO 9141-2", "5 baud init, 10.4 kbaud"),
    KWP2000_5BAUD(4, "ISO 14230-4 KWP", "5 baud init, 10.4 kbaud"),
    KWP2000_FAST(5, "ISO 14230-4 KWP", "Fast init, 10.4 kbaud"),
    CAN_11BIT_500K(6, "ISO 15765-4 CAN", "11 bit ID, 500 kbaud"),
    CAN_29BIT_500K(7, "ISO 15765-4 CAN", "29 bit ID, 500 kbaud"),
    CAN_11BIT_250K(8, "ISO 15765-4 CAN", "11 bit ID, 250 kbaud"),
    CAN_29BIT_250K(9, "ISO 15765-4 CAN", "29 bit ID, 250 kbaud"),
    USER1_CAN(11, "User1 CAN", "User1 CAN (11* bit ID, 125* kbaud)"),
    USER2_CAN(12, "User2 CAN", "User2 CAN (11* bit ID, 50* kbaud)");

    companion object {
        fun fromId(id: Int): OBD2Protocol {
            return entries.find { it.id == id } ?: AUTO
        }
    }
}
