package com.obelus

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.obelus.obelusscan.data.local.SettingsDataStore
import com.obelus.obelusscan.ui.dashboard.DashboardScreen
import com.obelus.obelusscan.ui.race.RaceScreen
import com.obelus.obelusscan.ui.settings.SettingsScreen
import com.obelus.presentation.ui.screens.DTCScreen
import com.obelus.presentation.ui.screens.HistoryScreen
import com.obelus.presentation.ui.screens.LogViewerScreen
import com.obelus.presentation.ui.screens.RaceHistoryScreen
import com.obelus.presentation.ui.screens.SecurityAccessScreen
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var settingsDataStore: SettingsDataStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val themeMode by settingsDataStore.themeMode.collectAsState(initial = "system")
            
            val darkTheme = when (themeMode) {
                "light" -> false
                "dark" -> true
                else -> isSystemInDarkTheme()
            }

            ObelusTheme(darkTheme = darkTheme) {
                MainScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val currentRoute = currentDestination?.route

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Text(
                        when (currentRoute) {
                            "dashboard"      -> "Dashboard"
                            "race"           -> "Race Mode"
                            "race_history"   -> "Historial de Carreras"
                            "dtc"            -> "Códigos DTC"
                            "history"        -> "Historial"
                            "log_viewer"     -> "Log Viewer"
                            "security_access" -> "Security Access 0x27"
                            "settings"       -> "Configuración"
                            else             -> "Obelus Scan"
                        }
                    )
                },
                actions = {
                    Icon(
                        imageVector = Icons.Default.Bluetooth,
                        contentDescription = "Bluetooth Status",
                        tint = Color.Gray,
                        modifier = Modifier.padding(end = 16.dp, start = 8.dp)
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        bottomBar = {
            NavigationBar {
                val items = listOf(
                    Triple("dashboard",  "Dash",    Icons.Default.Dashboard),
                    Triple("race",       "Race",    Icons.Default.Flag),
                    Triple("dtc",        "DTCs",    Icons.Default.Warning),
                    Triple("history",    "Historial",Icons.Default.History),
                    Triple("log_viewer", "Logs",    Icons.Default.TableChart),
                    Triple("settings",   "Settings", Icons.Default.Settings)
                )

                items.forEach { (route, label, icon) ->
                    NavigationBarItem(
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label) },
                        selected = currentDestination?.hierarchy?.any { it.route == route } == true,
                        onClick = {
                            navController.navigate(route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "dashboard",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("dashboard") { DashboardScreen() }
            composable("race") {
                RaceScreen(onNavigateToHistory = { navController.navigate("race_history") })
            }
            composable("dtc") { DTCScreen(onBack = { }) }
            composable("history") { HistoryScreen(onSessionClick = { }, onBack = { }) }
            composable("log_viewer") {
                LogViewerScreen(onBack = { navController.popBackStack() })
            }
            composable("security_access") {
                SecurityAccessScreen(onBack = { navController.popBackStack() })
            }
            composable("race_history") {
                RaceHistoryScreen(onBack = { navController.popBackStack() })
            }
            composable("settings") {
                SettingsScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}

@Composable
fun ObelusTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> darkColorScheme()
        else -> lightColorScheme()
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
