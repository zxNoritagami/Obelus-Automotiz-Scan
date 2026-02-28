package com.obelus.presentation.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.obelus.presentation.ui.navigation.ObelusNavigation

// ─────────────────────────────────────────────────────────────────────────────
// ObelusApp.kt
// Entry point de la UI del módulo de diagnóstico OBD2 (sub-app).
//
// NOTA: El proyecto ya tiene un MainScreen/MainActivity con ObelusTheme,
// ModalNavigationDrawer y un NavHost global. Este composable es el
// punto de entrada del flujo de diagnóstico BT que se inserta en el
// NavHost principal como ruta "obelus_scan".
//
// Integración en MainActivity (agregar en NavHost):
//   composable("obelus_scan") {
//       ObelusApp(onExit = { navController.popBackStack() })
//   }
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Sub-app del módulo de diagnóstico OBD2.
 *
 * Gestiona su propio [NavHostController] para las pantallas:
 *   - DeviceScanScreen (inicio) → ScanScreen → SignalListScreen / ScanSettingsScreen
 *
 * @param onExit    Callback al salir del módulo (navController.popBackStack() del host).
 * @param navController NavController propio del módulo (inyectable para tests).
 */
@Composable
fun ObelusApp(
    onExit: () -> Unit                      = {},
    navController: NavHostController        = rememberNavController()
) {
    ObelusNavigation(
        navController   = navController,
        onNavigateOut   = onExit
    )
}
