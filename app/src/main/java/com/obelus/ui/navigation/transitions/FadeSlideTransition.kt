package com.obelus.ui.navigation.transitions

import androidx.compose.animation.*
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.navigation.NavBackStackEntry

/**
 * Mapa de jerarquía para determinar la dirección de la animación.
 * El índice define la posición de izquierda a derecha en la barra de navegación.
 */
private val routeOrder = listOf(
    "dashboard",
    "race",
    "history",
    "log_viewer",
    "settings"
)

/**
 * Calcula la dirección de la animación basada en el orden de las rutas.
 * Retorna 1 para ir a la derecha (adelante), -1 para ir a la izquierda (atrás).
 */
fun getNavigationDirection(initialRoute: String?, targetRoute: String?): Int {
    if (initialRoute == null || targetRoute == null) return 1
    
    // Rutas especiales que no están en el menú principal (ej. splash, onboarding)
    if (initialRoute == "splash" || initialRoute == "onboarding") return 1
    
    val initialIndex = routeOrder.indexOf(initialRoute)
    val targetIndex = routeOrder.indexOf(targetRoute)
    
    // Si alguna ruta no está en el mapa, asumimos dirección hacia adelante
    if (initialIndex == -1 || targetIndex == -1) return 1 
    
    return if (targetIndex > initialIndex) 1 else -1
}

// Easing personalizado para una sensación más "Premium" y fluida
private val SmoothEasing = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f)
private const val AnimDuration = 450 // Aumentamos ligeramente para que se aprecie la fluidez

fun smartFadeSlideEnterTransition(direction: Int): EnterTransition {
    return slideInHorizontally(
        initialOffsetX = { direction * it },
        animationSpec = tween(durationMillis = AnimDuration, easing = SmoothEasing)
    ) + fadeIn(animationSpec = tween(durationMillis = AnimDuration))
}

fun smartFadeSlideExitTransition(direction: Int): ExitTransition {
    return slideOutHorizontally(
        targetOffsetX = { -direction * it },
        animationSpec = tween(durationMillis = AnimDuration, easing = SmoothEasing)
    ) + fadeOut(animationSpec = tween(durationMillis = AnimDuration))
}

// Mantener compatibilidad con funciones antiguas
fun fadeSlideEnterTransition() = smartFadeSlideEnterTransition(1)
fun fadeSlideExitTransition() = smartFadeSlideExitTransition(1)
fun fadeSlidePopEnterTransition() = smartFadeSlideEnterTransition(-1)
fun fadeSlidePopExitTransition() = smartFadeSlideExitTransition(-1)
