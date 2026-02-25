package com.obelus.ui.components.logs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.obelus.ui.theme.NeonCyan

data class CanFrameRaw(val timestamp: Long, val id: String, val dlc: Int, val data: ByteArray)

@Composable
fun HexDumpViewer(
    frames: List<CanFrameRaw>,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0F)) // Dark/Hacker background
            .padding(16.dp)
    ) {
        Column {
            // Header
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Text("OFFSET(T)", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.width(80.dp), fontFamily = FontFamily.Monospace)
                Text("ID", color = NeonCyan.copy(alpha = 0.7f), fontSize = 12.sp, modifier = Modifier.width(60.dp), fontFamily = FontFamily.Monospace)
                Text("DLC", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.width(40.dp), fontFamily = FontFamily.Monospace)
                Text("DATA (HEX)", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, modifier = Modifier.weight(1f), fontFamily = FontFamily.Monospace)
                Text("ASCII", color = Color.Gray.copy(alpha = 0.5f), fontSize = 12.sp, modifier = Modifier.width(80.dp), fontFamily = FontFamily.Monospace)
            }

            // Linea Hexa
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                itemsIndexed(frames) { index, frame ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Timestamp Format: 00:00.000 (Simplified for UI view)
                        val tStr = String.format("%08X", frame.timestamp % 0xFFFFFFFF)
                        Text(tStr, color = Color.DarkGray, fontSize = 14.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.width(80.dp))
                        
                        // ID Highlighted
                        Text(frame.id.padStart(4, '0'), color = NeonCyan, fontWeight = FontWeight.Bold, fontSize = 14.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.width(60.dp))
                        
                        // DLC
                        Text(frame.dlc.toString(), color = Color.Gray, fontSize = 14.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.width(40.dp))
                        
                        // Data Payload
                        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            frame.data.forEachIndexed { i, byte ->
                                val hex = String.format("%02X", byte)
                                // Syntax Highlighting: Last byte gray (simulate checksum/counter if desired), else white
                                val bColor = if (i == frame.data.lastIndex) Color.Gray else Color.White
                                Text(hex, color = bColor, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
                            }
                        }

                        // ASCII decode (replace non-printable with '.')
                        val asciiStr = frame.data.joinToString("") { byte ->
                            val c = byte.toInt().toChar()
                            if (c in ' '..'~') c.toString() else "."
                        }
                        Text(asciiStr, color = Color.Gray.copy(alpha = 0.5f), fontSize = 14.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.width(80.dp))
                    }
                }
            }
        }
    }
}
