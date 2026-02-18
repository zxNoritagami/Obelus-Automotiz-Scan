package com.obelus

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.obelus.presentation.ui.screens.ScanScreen
import com.obelus.presentation.ui.screens.SignalListScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ObelusTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ObelusApp()
                }
            }
        }
    }
}

@Composable
fun ObelusApp() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "scan") {
        composable("scan") {
            ScanScreen(
                onNavigateToDtcs = { /* TODO: Navigate to DTC screen */ }
            )
        }
        composable("signals") {
            SignalListScreen()
        }
    }
}

@Composable
fun ObelusTheme(content: @Composable () -> Unit) {
    // Basic Dark Theme wrapper for now
    MaterialTheme(
        colorScheme = androidx.compose.material3.darkColorScheme(),
        content = content
    )
}
