package com.obelus.data.ddt

import android.util.Xml
import com.obelus.domain.model.DdtCommand
import com.obelus.domain.model.DdtEcu
import com.obelus.domain.model.DdtParameter
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream

class DdtParser {

    fun parseEcu(inputStream: InputStream): DdtEcu? {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(inputStream, null)
        
        var ecuName = ""
        var protocol = ""
        var group = ""
        val parameters = mutableListOf<DdtParameter>()
        val commands = mutableListOf<DdtCommand>()

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            val tagName = parser.name
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (tagName) {
                        "Ecu" -> {
                            ecuName = parser.getAttributeValue(null, "name") ?: ""
                            protocol = parser.getAttributeValue(null, "protocol") ?: ""
                            group = parser.getAttributeValue(null, "group") ?: ""
                        }
                        "Parameter" -> {
                            parameters.add(parseParameter(parser))
                        }
                        "Command" -> {
                            commands.add(parseCommand(parser))
                        }
                    }
                }
            }
            eventType = parser.next()
        }
        
        return DdtEcu(ecuName, protocol, group, parameters, commands)
    }

    private fun parseParameter(parser: XmlPullParser): DdtParameter {
        val name = parser.getAttributeValue(null, "name") ?: ""
        val description = parser.getAttributeValue(null, "desc") ?: ""
        val byteOffset = parser.getAttributeValue(null, "byte")?.toIntOrNull() ?: 0
        val bitOffset = parser.getAttributeValue(null, "bit")?.toIntOrNull() ?: 0
        val length = parser.getAttributeValue(null, "length")?.toIntOrNull() ?: 8
        val minValue = parser.getAttributeValue(null, "min")?.toFloatOrNull() ?: 0f
        val maxValue = parser.getAttributeValue(null, "max")?.toFloatOrNull() ?: 0f
        val unit = parser.getAttributeValue(null, "unit") ?: ""
        val step = parser.getAttributeValue(null, "step")?.toFloatOrNull() ?: 1.0f
        val offset = parser.getAttributeValue(null, "offset")?.toFloatOrNull() ?: 0f
        
        // New attributes based on DDT4ALL expert logic
        val isSigned = parser.getAttributeValue(null, "signed") == "1"
        val isLittleEndian = parser.getAttributeValue(null, "endian")?.lowercase() == "little"
        
        val valueMap = mutableMapOf<Int, String>()
        
        var eventType = parser.next()
        while (!(eventType == XmlPullParser.END_TAG && parser.name == "Parameter")) {
            if (eventType == XmlPullParser.START_TAG && parser.name == "Value") {
                val v = parser.getAttributeValue(null, "value")?.toIntOrNull()
                val d = parser.getAttributeValue(null, "desc")
                if (v != null && d != null) valueMap[v] = d
            }
            eventType = parser.next()
        }

        return DdtParameter(
            name = name, 
            description = description, 
            byteOffset = byteOffset, 
            bitOffset = bitOffset, 
            length = length, 
            minValue = minValue, 
            maxValue = maxValue, 
            unit = unit, 
            step = step, 
            offset = offset,
            isSigned = isSigned,
            isLittleEndian = isLittleEndian,
            valueMap = valueMap
        )
    }

    private fun parseCommand(parser: XmlPullParser): DdtCommand {
        val name = parser.getAttributeValue(null, "name") ?: ""
        val description = parser.getAttributeValue(null, "desc") ?: ""
        val hexRequest = parser.getAttributeValue(null, "send") ?: ""
        
        var eventType = parser.next()
        while (!(eventType == XmlPullParser.END_TAG && parser.name == "Command")) {
            eventType = parser.next()
        }
        
        return DdtCommand(name, description, hexRequest)
    }
}
