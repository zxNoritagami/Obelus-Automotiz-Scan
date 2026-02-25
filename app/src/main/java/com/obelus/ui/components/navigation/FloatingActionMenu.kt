package com.obelus.ui.components.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import com.obelus.ui.theme.DarkBackground
import com.obelus.ui.theme.NeonCyan

@Composable
fun FloatingActionMenu(
    mainIcon: @Composable () -> Unit = { Icon(Icons.Default.Add, "Menu") },
    onDbcEditorClick: () -> Unit,
    onWebServerClick: () -> Unit,
    onActuatorClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    
    val rotateAnimation by animateFloatAsState(
        targetValue = if (isExpanded) 45f else 0f,
        animationSpec = tween(durationMillis = 300), 
        label = "rotateAnim"
    )

    Column(
        horizontalAlignment = Alignment.End,
        modifier = modifier
    ) {
        // Items Expandidos
        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut()
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                // Mini Fab 1: Web Server
                FloatingActionButton(
                    onClick = { 
                        onWebServerClick()
                        isExpanded = false
                    },
                    containerColor = DarkBackground,
                    contentColor = NeonCyan,
                    modifier = Modifier
                        .size(48.dp)
                        .padding(bottom = 8.dp)
                ) {
                    Text("WEB", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }

                // Mini Fab 2: DBC Editor
                FloatingActionButton(
                    onClick = { 
                        onDbcEditorClick()
                        isExpanded = false
                    },
                    containerColor = DarkBackground,
                    contentColor = NeonCyan,
                    modifier = Modifier.size(48.dp).padding(bottom = 8.dp)
                ) {
                    Text("DBC", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }

                // Mini Fab 3: Actuators
                FloatingActionButton(
                    onClick = {
                        onActuatorClick()
                        isExpanded = false
                    },
                    containerColor = DarkBackground,
                    contentColor = NeonCyan,
                    modifier = Modifier.size(48.dp)
                ) {
                    Text("TEST", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // FAB Principal
        FloatingActionButton(
            onClick = { isExpanded = !isExpanded },
            containerColor = NeonCyan,
            contentColor = DarkBackground,
            elevation = FloatingActionButtonDefaults.elevation(8.dp),
            shape = CircleShape
        ) {
            Box(modifier = Modifier.rotate(rotateAnimation)) {
                if (isExpanded) Icon(Icons.Default.Close, "Close") else mainIcon()
            }
        }
    }
}
// Placeholder imports para las fuentes en composable sueltas (se arreglaran en compilar/correr)
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
