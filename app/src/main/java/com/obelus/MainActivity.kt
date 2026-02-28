package com.obelus

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.obelus.obelusscan.data.local.SettingsDataStore
import com.obelus.obelusscan.ui.dashboard.DashboardScreen
import com.obelus.ui.screens.settings.SettingsScreen
import com.obelus.ui.screens.settings.ThemeSelectorScreen
import com.obelus.ui.screens.settings.ConnectionSettings
import com.obelus.ui.screens.onboarding.SplashScreen
import com.obelus.ui.screens.onboarding.OnboardingScreen
import com.obelus.ui.theme.*
import kotlinx.coroutines.launch
import androidx.hilt.navigation.compose.hiltViewModel
import com.obelus.ui.ddt4all.Ddt4allViewModel
import com.obelus.presentation.ui.screens.*
import com.obelus.presentation.ui.ObelusApp
import com.obelus.ui.screens.history.HistoryDetailScreen
import com.obelus.data.crash.CrashReporter
import com.obelus.ui.ddt4all.Ddt4allEcuListScreen
import com.obelus.ui.ddt4all.Ddt4allEcuDetailScreen
import com.obelus.ui.components.navigation.ModernBottomNav
import com.obelus.ui.components.navigation.NavItem
import com.obelus.ui.navigation.transitions.SharedElementProvider
import com.obelus.ui.navigation.transitions.*
import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import com.obelus.presentation.viewmodel.DiagnosticViewModel
import com.obelus.presentation.viewmodel.SecurityAccessViewModel
import com.obelus.presentation.viewmodel.WebServerViewModel

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var settingsDataStore: SettingsDataStore

    @Inject
    lateinit var crashReporter: CrashReporter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
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
        setIntent(intent)
    }

    // Lifecycle hooks for future scan-pause support
    override fun onResume() {
        super.onResume()
        // ScanViewModel observes ConnectionState; no manual action needed here.
    }

    override fun onPause() {
        super.onPause()
        // Optionally pause scan when app goes to background.
        // viewModel.pauseScan() — inject via Hilt if needed.
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
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    androidx.compose.runtime.LaunchedEffect(initialRoute) {
        if (initialRoute == "web_server") {
            navController.navigate("web_server") {
                popUpTo("dashboard") { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    if (isFirstLaunch == null) return

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = currentRoute !in listOf("splash", "onboarding"),
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = DarkBackground
            ) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "OBELUS EXPERT MENU",
                    modifier = Modifier.padding(16.dp),
                    style = FuturisticTypography.titleMedium,
                    color = NeonCyan
                )
                
                NavigationDrawerItem(
                    label = { Text("Historial de Sesiones", style = FuturisticTypography.labelLarge) },
                    selected = currentRoute == "history",
                    onClick = {
                        scope.launch { drawerState.close() }
                        navController.navigate("history")
                    },
                    icon = { Icon(Icons.Default.History, contentDescription = null) },
                    colors = NavigationDrawerItemDefaults.colors(
                        unselectedContainerColor = Color.Transparent,
                        selectedContainerColor = NeonCyan.copy(alpha = 0.1f),
                        unselectedTextColor = TextSecondary,
                        selectedTextColor = NeonCyan,
                        unselectedIconColor = TextSecondary,
                        selectedIconColor = NeonCyan
                    ),
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )

                NavigationDrawerItem(
                    label = { Text("Terminal Web", style = FuturisticTypography.labelLarge) },
                    selected = currentRoute == "web_server",
                    onClick = {
                        scope.launch { drawerState.close() }
                        navController.navigate("web_server")
                    },
                    icon = { Icon(Icons.Default.Language, contentDescription = null) },
                    colors = NavigationDrawerItemDefaults.colors(
                        unselectedContainerColor = Color.Transparent,
                        selectedContainerColor = NeonBlue.copy(alpha = 0.1f),
                        unselectedTextColor = TextSecondary,
                        selectedTextColor = NeonBlue,
                        unselectedIconColor = TextSecondary,
                        selectedIconColor = NeonBlue
                    ),
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )

                NavigationDrawerItem(
                    label = { Text("Análisis Bayesiano", style = FuturisticTypography.labelLarge) },
                    selected = currentRoute == "diagnostic_dashboard",
                    onClick = {
                        scope.launch { drawerState.close() }
                        navController.navigate("diagnostic_dashboard")
                    },
                    icon = { Icon(Icons.Default.Psychology, contentDescription = null) },
                    colors = NavigationDrawerItemDefaults.colors(
                        unselectedContainerColor = Color.Transparent,
                        selectedContainerColor = NeonGreen.copy(alpha = 0.1f),
                        unselectedTextColor = TextSecondary,
                        selectedTextColor = NeonGreen,
                        unselectedIconColor = TextSecondary,
                        selectedIconColor = NeonGreen
                    ),
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                if (currentRoute !in listOf("splash", "onboarding")) {
                    CenterAlignedTopAppBar(
                        title = { 
                            Text(
                                when (currentRoute) {
                                    "dashboard"           -> "Live Dashboard"
                                    "history"             -> "Logs de Sesión"
                                    "sniffer"             -> "Bus Sniffer"
                                    "settings"            -> "Configuración"
                                    "ddt4all_list"        -> "ECU Explorer"
                                    "diagnostic_dashboard" -> "Diagnóstico Pro"
                                    "security_access"     -> "Security Access"
                                    "web_server"          -> "Terminal Web"
                                    else                  -> "Obelus"
                                },
                                style = FuturisticTypography.titleMedium
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, contentDescription = "Menu", tint = NeonCyan)
                            }
                        },
                        actions = {
                            Icon(
                                imageVector = Icons.Default.Bluetooth,
                                contentDescription = "BT Status",
                                tint = NeonCyan,
                                modifier = Modifier.padding(end = 16.dp)
                            )
                        },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                            containerColor = DarkBackground,
                            titleContentColor = TextPrimary
                        )
                    )
                }
            },
            bottomBar = {
                if (currentRoute !in listOf("splash", "onboarding", "web_server", "diagnostic_dashboard", "security_access")) {
                    val navItems = listOf(
                        NavItem("dashboard", "Dash", Icons.Default.Dashboard, Icons.Outlined.Dashboard),
                        NavItem("ddt4all_list", "Explorer", Icons.Default.DeveloperBoard, Icons.Outlined.DeveloperBoard),
                        NavItem("sniffer", "Sniffer", Icons.Default.Sensors, Icons.Outlined.Sensors),
                        NavItem("settings", "Settings", Icons.Default.Settings, Icons.Outlined.Settings)
                    )
                    
                    ModernBottomNav(
                        items = navItems,
                        currentRoute = currentRoute,
                        onItemClick = { route ->
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
        ) { innerPadding ->
            SharedElementProvider {
                NavHost(
                    navController = navController,
                    startDestination = "splash",
                    modifier = Modifier.padding(innerPadding)
                ) {
                    composable("splash") {
                        SplashScreen(
                            onTimeout = {
                                val nextRoute = if (isFirstLaunch == true) "onboarding" else "dashboard"
                                navController.navigate(nextRoute) { popUpTo("splash") { inclusive = true } }
                            }
                        )
                    }
                    composable("onboarding") {
                        OnboardingScreen(
                            onFinishOnboarding = {
                                scope.launch { settingsDataStore.setFirstLaunchCompleted() }
                                navController.navigate("dashboard") { popUpTo("onboarding") { inclusive = true } }
                            }
                        )
                    }

                    composable("dashboard") { DashboardScreen() }
                    
                    composable("ddt4all_list") {
                        val ddt4ViewModel: Ddt4allViewModel = hiltViewModel()
                        Ddt4allEcuListScreen(
                            viewModel = ddt4ViewModel,
                            onNavigateToDetail = { navController.navigate("ddt4all_detail") },
                            onNavigateToDiagnostics = { navController.navigate("diagnostic_dashboard") },
                            onNavigateToSecurity = { navController.navigate("security_access") },
                            onBack = { navController.popBackStack() }
                        )
                    }
                    
                    composable("ddt4all_detail") {
                        val ddt4ViewModel: Ddt4allViewModel = hiltViewModel(navController.previousBackStackEntry ?: navController.currentBackStackEntry!!)
                        Ddt4allEcuDetailScreen(
                            viewModel = ddt4ViewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    
                    composable("sniffer") { SnifferScreen() }
                    
                    composable("settings") {
                        SettingsScreen(
                            dataStore = settingsDataStore,
                            onNavigateToTheme = { navController.navigate("theme_selector") },
                            onNavigateToConnection = { navController.navigate("connection_settings") },
                            onNavigateToCrashLogs = { navController.navigate("crash_logs") },
                            onBack = { navController.popBackStack() }
                        )
                    }
                    
                    composable("history") { 
                        HistoryScreen(
                            onSessionClick = { id -> navController.navigate("history_detail/$id") },
                            onBack = { navController.popBackStack() } 
                        ) 
                    }
                    
                    composable("history_detail/{id}") { backStackEntry ->
                        val id = backStackEntry.arguments?.getString("id")?.toLongOrNull() ?: 0L
                        HistoryDetailScreen(raceId = id, onBack = { navController.popBackStack() })
                    }
                    
                    composable("diagnostic_dashboard") {
                        val diagViewModel: DiagnosticViewModel = hiltViewModel()
                        DiagnosticDashboardScreen(viewModel = diagViewModel)
                    }
                    
                    composable("security_access") {
                        val securityViewModel: SecurityAccessViewModel = hiltViewModel()
                        SecurityAccessScreen(viewModel = securityViewModel, onBack = { navController.popBackStack() })
                    }

                    composable("web_server") {
                        val webViewModel: WebServerViewModel = hiltViewModel()
                        WebServerScreen(viewModel = webViewModel, onBack = { navController.popBackStack() })
                    }

                    // ── Módulo BT/OBD2 scan (Prompts 3–6) ─────────────────────────────
                    composable("obelus_scan") {
                        ObelusApp(
                            onExit = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ObelusTheme(themeMode: String = "system", content: @Composable () -> Unit) {
    val isSystemDark = isSystemInDarkTheme()
    val colorScheme = when (themeMode) {
        "oled" -> com.obelus.obelusscan.ui.theme.OledColorScheme
        "cyber" -> com.obelus.obelusscan.ui.theme.CyberColorScheme
        "sport" -> com.obelus.obelusscan.ui.theme.SportColorScheme
        "dark" -> com.obelus.obelusscan.ui.theme.SystemDarkColorScheme
        "light" -> com.obelus.obelusscan.ui.theme.SystemLightColorScheme
        else -> if (isSystemDark) com.obelus.obelusscan.ui.theme.SystemDarkColorScheme else com.obelus.obelusscan.ui.theme.SystemLightColorScheme
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
