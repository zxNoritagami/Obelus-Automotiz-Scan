package com.obelus.data.protocol.ddt4all

import com.obelus.data.local.entity.ddt4all.Ddt4allEcu
import com.obelus.data.ddt4all.Ddt4allParameter
import com.obelus.data.ddt4all.Ddt4allCommand
import com.obelus.data.ddt4all.Ddt4allValue
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory

class Ddt4allXmlParser {

    fun parseEcuFile(xmlContent: String, originFileName: String = "Unknown"): Ddt4allEcu {
        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val document = builder.parse(ByteArrayInputStream(xmlContent.toByteArray(Charsets.UTF_8)))
        
        document.documentElement.normalize()
        
        // Find Ecu node, fallback to documentElement if not found
        val ecuNodes = document.getElementsByTagName("Ecu")
        val ecuNode: Node = if (ecuNodes.length > 0) ecuNodes.item(0) else document.documentElement
        
        var ecuName = ""
        var protocol = ""
        var group = ""

        if (ecuNode is Element) {
            ecuName = ecuNode.getAttribute("name")
            protocol = ecuNode.getAttribute("protocol")
            group = ecuNode.getAttribute("group")
        }

        val targetNodes = document.getElementsByTagName("Target")
        if (targetNodes.length > 0) {
            val targetElement = targetNodes.item(0) as Element
            if (ecuName.isBlank()) ecuName = targetElement.getAttribute("name")
            if (protocol.isBlank()) protocol = targetElement.getAttribute("href")
        }

        val parsedParameters = parseParameters(ecuNode)
        val parsedCommands = parseCommands(ecuNode)

        val ecu = Ddt4allEcu(
            name = ecuName.ifBlank { "Unknown ECU" },
            protocol = protocol.ifBlank { "Unknown Protocol" },
            group = group.ifBlank { "Unknown Group" },
            originFile = originFileName
        )
        ecu.parameters = parsedParameters
        ecu.commands = parsedCommands
        return ecu
    }

    private fun parseParameters(ecuNode: Node): List<Ddt4allParameter> {
        val parameters = mutableListOf<Ddt4allParameter>()
        if (ecuNode !is Element) return parameters

        val paramNodes = ecuNode.getElementsByTagName("Parameter")
        for (i in 0 until paramNodes.length) {
            val pNode = paramNodes.item(i)
            if (pNode is Element && pNode.parentNode?.nodeName == "Parameters") {
                val name = pNode.getAttribute("name")
                val desc = pNode.getAttribute("desc")
                val byte = pNode.getAttribute("byte").toIntOrNull() ?: 0
                val bit = pNode.getAttribute("bit").toIntOrNull() ?: 0
                val length = pNode.getAttribute("length").toIntOrNull() ?: 0
                val min = pNode.getAttribute("min").toDoubleOrNull() ?: 0.0
                val max = pNode.getAttribute("max").toDoubleOrNull() ?: 0.0
                val unit = pNode.getAttribute("unit")
                val step = pNode.getAttribute("step").toDoubleOrNull() ?: 1.0
                val targetId = pNode.getAttribute("targetId")

                val values = mutableListOf<Ddt4allValue>()
                val valueNodes = pNode.getElementsByTagName("Value")
                for (j in 0 until valueNodes.length) {
                    val vNode = valueNodes.item(j)
                    if (vNode is Element) {
                        val vValue = vNode.getAttribute("value")
                        val vDesc = vNode.getAttribute("desc")
                        values.add(Ddt4allValue(vValue, vDesc))
                    }
                }

                parameters.add(
                    Ddt4allParameter(
                        name = name,
                        desc = desc,
                        byte = byte,
                        bit = bit,
                        length = length,
                        min = min,
                        max = max,
                        unit = unit,
                        step = step,
                        targetId = targetId,
                        values = values
                    )
                )
            }
        }
        return parameters
    }

    private fun parseCommands(ecuNode: Node): List<Ddt4allCommand> {
        val commands = mutableListOf<Ddt4allCommand>()
        if (ecuNode !is Element) return commands

        val commandNodes = ecuNode.getElementsByTagName("Command")
        for (i in 0 until commandNodes.length) {
            val cNode = commandNodes.item(i)
            if (cNode is Element) {
                val name = cNode.getAttribute("name")
                val desc = cNode.getAttribute("desc")
                val code = cNode.getAttribute("send")

                val parameters = mutableListOf<String>()
                val sendNodes = cNode.getElementsByTagName("Send")
                for (j in 0 until sendNodes.length) {
                    val sendNode = sendNodes.item(j)
                    if (sendNode is Element) {
                        val paramNodes = sendNode.getElementsByTagName("Parameter")
                        for (k in 0 until paramNodes.length) {
                            val pNode = paramNodes.item(k)
                            if (pNode is Element) {
                                val pName = pNode.getAttribute("name")
                                if (pName.isNotEmpty()) {
                                    parameters.add(pName)
                                }
                            }
                        }
                    }
                }

                commands.add(
                    Ddt4allCommand(
                        name = name,
                        desc = desc,
                        code = code,
                        parameters = parameters
                    )
                )
            }
        }
        return commands
    }
}
