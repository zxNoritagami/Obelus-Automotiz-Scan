package com.obelus.ui.components.dtc

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.obelus.ui.theme.DarkBackground
import com.obelus.ui.theme.DtcCritical
import kotlinx.coroutines.delay

@Composable
fun ClearDtcDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    var countdown by remember { mutableStateOf(3) }
    var isEnabled by remember { mutableStateOf(false) }

    // Cuenta regresiva 3 segundos
    LaunchedEffect(Unit) {
        while (countdown > 0) {
            delay(1000)
            countdown--
        }
        isEnabled = true
    }

    // Ping-pong pulse effect para el icono de alerta
    val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = tween(800),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ), label = "pulse"
    )

    Dialog(
        onDismissRequest = { if (!isEnabled) return@Dialog else onDismiss() },
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = DarkBackground)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Icono animado
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = DtcCritical,
                    modifier = Modifier.size(64.dp).scale(scale)
                )

                Spacer(Modifier.height(16.dp))

                Text(
                    text = "¿ELIMINAR CÓDIGOS?",
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = "Vas a enviar el comando de limpieza a la ECU responsable. Esta acción borrará permanentemente los registros actuales del Check Engine.",
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(Modifier.height(32.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(50.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Text("CANCELAR")
                    }

                    Button(
                        onClick = onConfirm,
                        enabled = isEnabled,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DtcCritical,
                            disabledContainerColor = DtcCritical.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.weight(1f).height(50.dp)
                    ) {
                        if (isEnabled) {
                            Text("BORRAR", color = Color.White, fontWeight = FontWeight.Bold)
                        } else {
                            Text("ESPERA ($countdown)", color = Color.White.copy(alpha = 0.5f))
                        }
                    }
                }
            }
        }
    }
}
