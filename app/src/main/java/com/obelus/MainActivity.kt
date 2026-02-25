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
import com.obelus.ui.screens.settings.SettingsScreen
import com.obelus.ui.screens.settings.ThemeSelectorScreen
import com.obelus.ui.screens.settings.ConnectionSettings
import com.obelus.ui.screens.onboarding.SplashScreen
import com.obelus.ui.screens.onboarding.OnboardingScreen
import com.obelus.ui.theme.*
import kotlinx.coroutines.launch
import androidx.hilt.navigation.compose.hiltViewModel
import com.obelus.ui.ddt4all.Ddt4allViewModel
import com.obelus.presentation.ui.screens.DTCScreen
import com.obelus.presentation.ui.screens.DbcEditorScreen
import com.obelus.presentation.ui.screens.HistoryScreen
import com.obelus.presentation.ui.screens.LogViewerScreen
import com.obelus.presentation.ui.screens.RaceHistoryScreen
import com.obelus.presentation.ui.screens.SecurityAccessScreen
import com.obelus.presentation.ui.screens.WebServerScreen
import com.obelus.presentation.ui.screens.CrashLogScreen
import com.obelus.presentation.ui.screens.ActuatorTestScreen
import com.obelus.ui.screens.actuators.ActuatorTestsScreen
import com.obelus.ui.screens.actuators.ActuatorTestDetailScreen
import com.obelus.ui.screens.history.HistoryScreen
import com.obelus.ui.screens.history.HistoryDetailScreen
import com.obelus.data.crash.CrashReporter
import com.obelus.ui.ddt4all.Ddt4allEcuListScreen
import com.obelus.ui.ddt4all.Ddt4allEcuDetailScreen
import com.obelus.ui.components.navigation.FloatingActionMenu
import com.obelus.ui.components.navigation.ModernBottomNav
import com.obelus.ui.components.navigation.NavItem
import com.obelus.ui.navigation.transitions.SharedElementProvider
import com.obelus.ui.navigation.transitions.fadeSlideEnterTransition
import com.obelus.ui.navigation.transitions.fadeSlideExitTransition
import com.obelus.ui.navigation.transitions.fadeSlidePopEnterTransition
import com.obelus.ui.navigation.transitions.fadeSlidePopExitTransition
import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var settingsDataStore: SettingsDataStore

    @Inject
    lateinit var crashReporter: CrashReporter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Request POST_NOTIFICATIONS for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        val navigateTo = intent.getStringExtra("navigate_to")

        setContent {
            val themeMode by settingsDataStore.themeMode.collectAsState(initial = "system")
            
            ObelusTheme(themeMode = themeMode) {
                MainScreen(crashReporter, settingsDataStore, navigateTo)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Ensure composables react to new intent if Activity was already running
        setIntent(intent) 
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(crashReporter: CrashReporter, settingsDataStore: SettingsDataStore, initialRoute: String?) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val currentRoute = currentDestination?.route

    val isFirstLaunch by settingsDataStore.isFirstLaunch.collectAsState(initial = null)
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    // Manejar enrutamiento desde Notificación
    androidx.compose.runtime.LaunchedEffect(initialRoute) {
        if (initialRoute == "web_server") {
            navController.navigate("web_server") {
                popUpTo("dashboard") { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    if (isFirstLaunch == null) return // Carga inicial DataStore

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
                            "web_server"     -> "Dashboard Web"
                            "security_access" -> "Security Access 0x27"
                            "dbc_editor"     -> "Editor DBC"
                            "actuator_test"  -> "Tests de Actuadores"
                            "settings"       -> "Configuración"
                            "ddt4all_list"   -> "ECU Explorer"
                            "ddt4all_detail" -> "ECU Detalles"
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
        floatingActionButton = {
            FloatingActionMenu(
                onDbcEditorClick = { navController.navigate("dbc_editor") },
                onWebServerClick = { navController.navigate("web_server") },
                onActuatorClick = { navController.navigate("actuators") }
            )
        },
        bottomBar = {
            if (currentRoute !in listOf("splash", "onboarding")) {
                val navItems = listOf(
                    NavItem("dashboard", "Dash", Icons.Default.Dashboard, Icons.Default.Dashboard),
                    NavItem("race", "Race", Icons.Default.Flag, Icons.Default.Flag),
                    NavItem("history", "Historial", Icons.Default.History, Icons.Default.History),
                    NavItem("log_viewer", "Logs", Icons.Default.ListAlt, Icons.Default.ListAlt),
                    NavItem("settings", "Settings", Icons.Default.Settings, Icons.Default.Settings)
                )
                
                ModernBottomNav(
                    items = navItems,
                    currentRoute = currentRoute,
                    onItemClick = { route ->
                        navController.navigate(route) {
                            popUpTo("dashboard") {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        SharedElementProvider {
            NavHost(
                navController = navController,
                startDestination = "splash",
                modifier = Modifier.padding(innerPadding),
                enterTransition = { fadeSlideEnterTransition() },
                exitTransition = { fadeSlideExitTransition() },
                popEnterTransition = { fadeSlidePopEnterTransition() },
                popExitTransition = { fadeSlidePopExitTransition() }
            ) {
                val ddt4allViewModel: Ddt4allViewModel? = null // will be instantiated in the parent scope where valid context is active or initialized per screen
                composable("splash") {
                    SplashScreen(
                        onTimeout = {
                            val nextRoute = if (isFirstLaunch == true) "onboarding" else "dashboard"
                            navController.navigate(nextRoute) {
                                popUpTo("splash") { inclusive = true }
                            }
                        }
                    )
                }
                composable("onboarding") {
                    OnboardingScreen(
                        onFinishOnboarding = {
                            scope.launch { settingsDataStore.setFirstLaunchCompleted() }
                            navController.navigate("dashboard") {
                                popUpTo("onboarding") { inclusive = true }
                            }
                        }
                    )
                }

                composable("dashboard") { DashboardScreen() }
            composable("race") {
                RaceScreen(onNavigateToHistory = { navController.navigate("race_history") })
            }
            composable("dtc") { DTCScreen(onBack = { }) }
            composable("history") { 
                HistoryScreen(
                    onNavigateToDetail = { id -> navController.navigate("history_detail/$id") },
                    onBack = { navController.popBackStack() } 
                ) 
            }
            composable("history_detail/{id}") { backStackEntry ->
                val id = backStackEntry.arguments?.getString("id")?.toLongOrNull() ?: 0L
                HistoryDetailScreen(raceId = id, onBack = { navController.popBackStack() })
            }
            composable("actuators") {
                ActuatorTestsScreen(
                    onNavigateToTest = { id -> navController.navigate("actuator_test_detail/$id") },
                    onBack = { navController.popBackStack() }
                )
            }
            composable("actuator_test_detail/{id}") { backStackEntry ->
                val catId = backStackEntry.arguments?.getString("id")?.toIntOrNull() ?: 0
                ActuatorTestDetailScreen(categoryId = catId, onBack = { navController.popBackStack() })
            }
            composable("web_server") { WebServerScreen() }
            composable("log_viewer") {
                LogViewerScreen(onBack = { navController.popBackStack() })
            }
            composable("security_access") {
                SecurityAccessScreen(onBack = { navController.popBackStack() })
            }
            composable("race_history") {
                RaceHistoryScreen(onBack = { navController.popBackStack() })
            }
            composable(
                "dbc_editor",
                enterTransition = null,
                exitTransition = null,
                popEnterTransition = null,
                popExitTransition = null
            ) {
                DbcEditorScreen(onBack = { navController.popBackStack() })
            }
            composable("settings") {
                SettingsScreen(
                    dataStore = settingsDataStore,
                    onNavigateToTheme = { navController.navigate("theme_selector") },
                    onNavigateToConnection = { navController.navigate("connection_settings") },
                    onNavigateToCrashLogs = { navController.navigate("crash_logs") },
                    onBack = { navController.popBackStack() }
                )
            }
            composable("theme_selector") { ThemeSelectorScreen(settingsDataStore, onBack = { navController.popBackStack() }) }
            composable("connection_settings") { ConnectionSettings(settingsDataStore, onBack = { navController.popBackStack() }) }
            composable("actuator_test") { ActuatorTestScreen() }
            composable("crash_logs") {
                CrashLogScreen(
                    crashReporter = crashReporter,
                    onBack = { navController.popBackStack() }
                )
            }
            composable("ddt4all_list") {
                val ddt4ViewModel: Ddt4allViewModel = hiltViewModel(navController.currentBackStackEntry!!)
                Ddt4allEcuListScreen(
                    viewModel = ddt4ViewModel,
                    onNavigateToDetail = { navController.navigate("ddt4all_detail") },
                    onBack = { navController.popBackStack() }
                )
            }
                composable("ddt4all_detail") {
                    // Ensure same viewModel instance across ecu flow
                    val ddt4ViewModel: Ddt4allViewModel = hiltViewModel(navController.previousBackStackEntry ?: navController.currentBackStackEntry!!)
                    Ddt4allEcuDetailScreen(
                        viewModel = ddt4ViewModel,
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}

@Composable
fun ObelusTheme(
    themeMode: String = "system",
    content: @Composable () -> Unit
) {
    val isSystemDark = isSystemInDarkTheme()
    
    val colorScheme = when (themeMode) {
        "oled" -> OledColorScheme
        "cyber" -> CyberColorScheme
        "sport" -> SportColorScheme
        "dark" -> SystemDarkColorScheme
        "light" -> SystemLightColorScheme
        else -> if (isSystemDark) SystemDarkColorScheme else SystemLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
