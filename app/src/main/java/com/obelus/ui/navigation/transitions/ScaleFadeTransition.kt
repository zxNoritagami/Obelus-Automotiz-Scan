package com.obelus.ui.navigation.transitions

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween

/**
 * Scale Up + Fade (Ideal para overlays transparentes, dialogs pop-up o victorias de carreras)
 */
fun scaleFadeEnterTransition(): EnterTransition {
    return scaleIn(
        initialScale = 0.8f,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
    ) + fadeIn(animationSpec = tween(durationMillis = 300))
}

fun scaleFadeExitTransition(): ExitTransition {
    return scaleOut(
        targetScale = 0.8f,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
    ) + fadeOut(animationSpec = tween(durationMillis = 300))
}
