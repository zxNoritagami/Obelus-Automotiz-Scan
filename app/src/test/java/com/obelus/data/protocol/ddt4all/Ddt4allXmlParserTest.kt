package com.obelus.data.protocol.ddt4all

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Ddt4allXmlParserTest {

    @Test
    fun testParseEcuFile() {
        val xmlContent = """
            <?xml version="1.0" encoding="utf-8"?>
            <Ecu name="TestECU" protocol="CAN" group="Engine">
                <Target name="TestTarget" href="CAN_Protocol"/>
                <Parameters>
                    <Parameter name="RPM" desc="Engine Speed" byte="3" bit="0" length="16" min="0" max="8000" unit="RPM" step="1.0" targetId="T1">
                        <Value value="0" desc="Off" />
                        <Value value="1" desc="On" />
                    </Parameter>
                </Parameters>
                <Commands>
                    <Command name="Reset" desc="Reset ECU" send="11 01">
                        <Send>
                            <Parameter name="RPM" />
                        </Send>
                    </Command>
                </Commands>
            </Ecu>
        """.trimIndent()

        val parser = Ddt4allXmlParser()
        val ecu = parser.parseEcuFile(xmlContent, "test.xml")

        // Assert ECU basic info
        assertEquals("TestECU", ecu.name)
        assertEquals("CAN", ecu.protocol)
        assertEquals("Engine", ecu.group)

        // Assert Parameters
        assertNotNull(ecu.parameters)
        assertEquals(1, ecu.parameters.size)
        val param = ecu.parameters[0]
        assertEquals("RPM", param.name)
        assertEquals("Engine Speed", param.desc)
        assertEquals(3, param.byte)
        assertEquals(16, param.length)
        assertEquals(8000.0, param.max, 0.001)
        assertEquals("RPM", param.unit)
        assertEquals(2, param.values.size)
        assertEquals("Off", param.values[0].desc)

        // Assert Commands
        assertNotNull(ecu.commands)
        assertEquals(1, ecu.commands.size)
        val cmd = ecu.commands[0]
        assertEquals("Reset", cmd.name)
        assertEquals("Reset ECU", cmd.desc)
        assertEquals("11 01", cmd.code)
        assertEquals(1, cmd.parameters.size)
        assertEquals("RPM", cmd.parameters[0])
    }
}
