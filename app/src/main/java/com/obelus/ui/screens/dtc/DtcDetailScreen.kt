package com.obelus.ui.screens.dtc

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.obelus.data.repository.EnrichedDtc
import com.obelus.data.repository.SeverityLevel
import com.obelus.ui.components.dtc.DtcSeverity
import com.obelus.ui.components.dtc.DtcSeverityBadge
import com.obelus.ui.theme.DarkBackground
import com.obelus.ui.theme.DeepSurface
import com.obelus.ui.theme.NeonCyan

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DtcDetailScreen(
    dtc: EnrichedDtc,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val severityEnum = when (dtc.severityLevel) {
        SeverityLevel.ERROR -> DtcSeverity.CRITICAL
        SeverityLevel.WARNING -> DtcSeverity.WARNING
        SeverityLevel.INFO -> DtcSeverity.INFO
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("DETALLE DTC", color = Color.White, letterSpacing = 1.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
            )
        },
        containerColor = DarkBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(16.dp))

            // Header Code
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = dtc.dtc.code,
                    fontSize = 64.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    fontWeight = FontWeight.Black,
                    color = severityEnum.color
                )
                DtcSeverityBadge(severity = severityEnum)
            }

            Spacer(Modifier.height(8.dp))
            
            // Sub Header Info — chips informan sistema y ECU al tocarse
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = {
                        Toast.makeText(
                            context,
                            "Sistema: ${dtc.systemLabel}",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    label = { Text(dtc.systemLabel, color = Color.White) },
                    colors = AssistChipDefaults.assistChipColors(containerColor = DeepSurface)
                )
                AssistChip(
                    onClick = {
                        Toast.makeText(
                            context,
                            "Fabricante ECU: ${dtc.manufacturer}",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    label = { Text("ECU: ${dtc.manufacturer}", color = Color.White) },
                    colors = AssistChipDefaults.assistChipColors(containerColor = DeepSurface)
                )
            }

            Spacer(Modifier.height(24.dp))

            // Description Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DeepSurface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "DESCRIPCIÓN",
                        color = Color.Gray,
                        fontSize = 12.sp,
                        letterSpacing = 1.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = dtc.descriptionEs,
                        color = Color.White,
                        fontSize = 16.sp,
                        lineHeight = 24.sp
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Causes Card
            if (dtc.possibleCauses.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                    colors = CardDefaults.cardColors(containerColor = DeepSurface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "POSIBLES CAUSAS",
                            color = Color.Gray,
                            fontSize = 12.sp,
                            letterSpacing = 1.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(12.dp))
                        dtc.possibleCauses.forEach { cause ->
                            Row(modifier = Modifier.padding(bottom = 8.dp)) {
                                Box(
                                    modifier = Modifier
                                        .padding(top = 6.dp, end = 8.dp)
                                        .size(6.dp)
                                        .clip(androidx.compose.foundation.shape.CircleShape)
                                        .background(severityEnum.color)
                                )
                                Text(
                                    text = cause,
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Action Button — abre obdcodes.com con el código DTC
            Button(
                onClick = {
                    val url = "https://www.obdcodes.com/${dtc.dtc.code.lowercase()}"
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth().height(56.dp).padding(bottom = 16.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
            ) {
                Icon(Icons.Default.MenuBook, contentDescription = null, tint = Color.Black)
                Spacer(Modifier.width(8.dp))
                Text("BUSCAR ${dtc.dtc.code} EN WEB", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    }
}
