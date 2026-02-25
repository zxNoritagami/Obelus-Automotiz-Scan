package com.obelus.ui.navigation.transitions

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable

/**
 * Slide Horizontal + Fade In/Out (Ideal para NavHost Main -> Dash -> Race)
 */
fun fadeSlideEnterTransition(): EnterTransition {
    return slideInHorizontally(
        initialOffsetX = { it }, // Desliza desde la derecha
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
    ) + fadeIn(animationSpec = tween(durationMillis = 300))
}

fun fadeSlideExitTransition(): ExitTransition {
    return slideOutHorizontally(
        targetOffsetX = { -it }, // Desliza hacia la izquierda
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
    ) + fadeOut(animationSpec = tween(durationMillis = 300))
}

fun fadeSlidePopEnterTransition(): EnterTransition {
    return slideInHorizontally(
        initialOffsetX = { -it }, // Vuelve desde la izquierda
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
    ) + fadeIn(animationSpec = tween(durationMillis = 300))
}

fun fadeSlidePopExitTransition(): ExitTransition {
    return slideOutHorizontally(
        targetOffsetX = { it }, // Sale hacia la derecha
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
    ) + fadeOut(animationSpec = tween(durationMillis = 300))
}
