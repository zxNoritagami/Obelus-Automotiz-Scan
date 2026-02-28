package com.obelus.presentation.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.obelus.presentation.ui.screens.*

// ─────────────────────────────────────────────────────────────────────────────
// ObelusNavigation.kt
// Graph de navegación del módulo de diagnóstico OBD2.
// Integrado como sub-graph desde el MainActivity NavHost existente.
// ─────────────────────────────────────────────────────────────────────────────

/** Rutas del módulo de escaneo. */
object ScanRoutes {
    const val DEVICE_SCAN  = "device_scan"
    const val SCAN         = "scan"
    const val SIGNAL_LIST  = "signal_list"
    const val SCAN_SETTINGS = "scan_settings"
}

/**
 * NavHost del módulo de diagnóstico BT + OBD2.
 *
 * Flujo principal:
 *   device_scan (start) → scan ← → signal_list
 *                                 ↘ scan_settings
 *
 * Transiciones:
 *   device_scan → scan : slide in from right
 *   scan → device_scan : slide out to right
 *   scan → signal_list : fade
 *
 * @param navController     Controlador propio o el del MainScreen.
 * @param startDestination  Ruta inicial (default [ScanRoutes.DEVICE_SCAN]).
 * @param onNavigateOut     Callback para salir del módulo (p.ej. popear al main navController).
 */
@Composable
fun ObelusNavigation(
    navController: NavHostController = rememberNavController(),
    startDestination: String         = ScanRoutes.DEVICE_SCAN,
    onNavigateOut: () -> Unit        = {}
) {
    NavHost(
        navController    = navController,
        startDestination = startDestination
    ) {

        // ── device_scan ───────────────────────────────────────────────────────
        composable(
            route = ScanRoutes.DEVICE_SCAN,
            enterTransition = {
                slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(300)) +
                fadeIn(animationSpec = tween(300))
            },
            exitTransition = {
                slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween(300)) +
                fadeOut(animationSpec = tween(200))
            },
            popEnterTransition = {
                slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(300)) +
                fadeIn(animationSpec = tween(300))
            },
            popExitTransition = {
                slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300)) +
                fadeOut(animationSpec = tween(200))
            }
        ) {
            DeviceScanContent(
                onNavigateToScan = {
                    navController.navigate(ScanRoutes.SCAN) {
                        launchSingleTop = true
                    }
                }
            )
        }

        // ── scan ──────────────────────────────────────────────────────────────
        composable(
            route = ScanRoutes.SCAN,
            enterTransition = {
                slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(320)) +
                fadeIn(animationSpec = tween(320))
            },
            exitTransition  = {
                slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(280)) +
                fadeOut(animationSpec = tween(200))
            },
            popEnterTransition = {
                slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(280)) +
                fadeIn(animationSpec = tween(280))
            },
            popExitTransition = {
                slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300)) +
                fadeOut(animationSpec = tween(200))
            }
        ) {
            ScanScreen(
                onNavigateToDeviceScan = {
                    navController.navigate(ScanRoutes.DEVICE_SCAN) {
                        popUpTo(ScanRoutes.DEVICE_SCAN) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onNavigateToHistory = onNavigateOut,
                onNavigateToDtcs    = {
                    // Los DTCs ya están embebidos en ScanScreen via DtcSection.
                    // Aquí podría navegar a una pantalla DTCs dedicada si existe.
                }
            )
        }

        // ── signal_list ───────────────────────────────────────────────────────
        composable(
            route = ScanRoutes.SIGNAL_LIST,
            enterTransition  = { fadeIn(animationSpec  = tween(250)) },
            exitTransition   = { fadeOut(animationSpec = tween(200)) },
            popEnterTransition  = { fadeIn(animationSpec  = tween(250)) },
            popExitTransition   = { fadeOut(animationSpec = tween(200)) }
        ) {
            SignalListScreen()
        }

        // ── scan_settings ─────────────────────────────────────────────────────
        composable(
            route = ScanRoutes.SCAN_SETTINGS,
            enterTransition = {
                slideInVertically(initialOffsetY = { it / 2 }, animationSpec = tween(300)) +
                fadeIn(animationSpec = tween(300))
            },
            exitTransition = {
                slideOutVertically(targetOffsetY = { it / 2 }, animationSpec = tween(250)) +
                fadeOut(animationSpec = tween(200))
            }
        ) {
            ScanSettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
