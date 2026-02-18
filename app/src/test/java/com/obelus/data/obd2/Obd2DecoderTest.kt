package com.obelus.data.obd2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class Obd2DecoderTest {

    @Test
    fun `decode RPM with valid data returns correct value`() {
        // Input: 41 0C 1B 56 -> ((0x1B * 256) + 0x56) / 4 = ((27*256)+86)/4 = 6998/4 = 1749.5
        // User example: 6990/4 = 1747.5 (0x1B=27, 0x56=86. 27*256+86 = 6998. 6998/4 = 1749.5)
        // Wait, 0x1B56:
        // 1B = 16+11 = 27. 27*256 = 6912.
        // 56 = 5*16+6 = 80+6 = 86.
        // 6912+86 = 6998. 6998/4 = 1749.5.
        
        val result = Obd2Decoder.decode("41 0C 1B 56")
        assertNotNull(result)
        assertEquals(1749.5f, result?.value)
        assertEquals("rpm", result?.unit)
    }
    
    @Test
    fun `decode RPM without spaces returns correct value`() {
        val result = Obd2Decoder.decode("410C1B56")
        assertNotNull(result)
        assertEquals(1749.5f, result?.value)
    }

    @Test
    fun `decode Speed returns correct value`() {
        // 41 0D 32 -> 0x32 = 50 km/h
        val result = Obd2Decoder.decode("41 0D 32")
        assertNotNull(result)
        assertEquals(50f, result?.value)
        assertEquals("km/h", result?.unit)
    }

    @Test
    fun `decode Coolant Temp returns correct values`() {
        // 41 05 5A -> 0x5A = 90. 90-40 = 50.
        val result = Obd2Decoder.decode("41 05 5A")
        assertNotNull(result)
        assertEquals(50f, result?.value)
        assertEquals("°C", result?.unit)
    }

    @Test
    fun `decode Engine Load returns correct value`() {
        // 41 04 80 -> 0x80 = 128. (128*100)/255 = 50.196...
        val result = Obd2Decoder.decode("41 04 80")
        assertNotNull(result)
        assertEquals(50.19608f, result!!.value, 0.001f)
    }
    
    @Test
    fun `decode returns null for invalid PID`() {
        val result = Obd2Decoder.decode("41 FF 00") // FF not supported
        assertNull(result)
    }
    
    @Test
    fun `decode returns null for bad mode`() {
        val result = Obd2Decoder.decode("43 0C 00 00") // 43 is Error or Mode 3 response? Mode 1 resp is 41. 
        // Actually 7F is error. 43 is response to Mode 03.
        assertNull(result)
    }
}
