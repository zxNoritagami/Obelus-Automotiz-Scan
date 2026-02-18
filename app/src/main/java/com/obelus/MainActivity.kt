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
import com.obelus.presentation.ui.screens.HistoryScreen
import com.obelus.presentation.ui.screens.PermisoScreen
import com.obelus.presentation.ui.screens.SessionDetailScreen
import com.obelus.presentation.ui.screens.DTCScreen
import dagger.hilt.android.AndroidEntryPoint
import androidx.navigation.NavType
import androidx.navigation.navArgument

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

    NavHost(navController = navController, startDestination = "permisos") {
        composable("permisos") {
            PermisoScreen(
                onPermisosConcedidos = {
                    navController.navigate("scan") {
                        popUpTo("permisos") { inclusive = true }
                    }
                }
            )
        }
        composable("scan") {
            ScanScreen(
                onNavigateToDtcs = { navController.navigate("dtcs") },
                onNavigateToHistory = { navController.navigate("history") }
            )
        }
        composable("signals") {
            SignalListScreen()
        }
        composable("history") {
            HistoryScreen(
                onSessionClick = { sessionId -> navController.navigate("session_detail/$sessionId") },
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            route = "session_detail/{sessionId}",
            arguments = listOf(navArgument("sessionId") { type = NavType.LongType })
        ) { backStackEntry ->
            SessionDetailScreen(
                sessionId = backStackEntry.arguments?.getLong("sessionId") ?: 0L,
                onBack = { navController.popBackStack() }
            )
        }
        composable("dtcs") {
            DTCScreen(onBack = { navController.popBackStack() })
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
