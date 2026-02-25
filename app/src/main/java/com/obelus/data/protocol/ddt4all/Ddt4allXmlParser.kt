package com.obelus.data.protocol.ddt4all

import com.obelus.data.local.entity.ddt4all.Ddt4allEcu
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader

class Ddt4allXmlParser {

    /**
     * Parses the XML content and extracts the ECU information.
     * Currently returns a Ddt4allEcu with the parsed basic information.
     * (Lists of parameters and commands will be handled in separate parsing logic/entities
     * since the requirement specifies returning just the Ddt4allEcu entity).
     */
    fun parseEcuFile(xmlContent: String, originFileName: String = "Unknown"): Ddt4allEcu {
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = true
        val parser = factory.newPullParser()
        parser.setInput(StringReader(xmlContent))

        var eventType = parser.eventType
        var ecuName = ""
        var protocol = ""
        var group = ""
        
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    val tagName = parser.name
                    if (tagName.equals("Ecu", ignoreCase = true)) {
                        // Extract attributes if they are defined on the Ecu tag directly
                        ecuName = parser.getAttributeValue(null, "name") ?: ecuName
                        protocol = parser.getAttributeValue(null, "protocol") ?: protocol
                        group = parser.getAttributeValue(null, "group") ?: group
                    } else if (tagName.equals("Target", ignoreCase = true)) {
                        // Some DDT4ALL files store name/protocol inside a Target node
                        ecuName = parser.getAttributeValue(null, "name") ?: ecuName
                        protocol = parser.getAttributeValue(null, "href") ?: protocol
                    }
                }
            }
            eventType = parser.next()
        }

        return Ddt4allEcu(
            name = ecuName.ifBlank { "Unknown ECU" },
            protocol = protocol.ifBlank { "Unknown Protocol" },
            group = group.ifBlank { "Unknown Group" },
            originFile = originFileName
        )
    }
    
    // Future parsing of Commands and Parameters would go here
}
