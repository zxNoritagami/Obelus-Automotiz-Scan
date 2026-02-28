package com.obelus.ui.navigation.transitions

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.foundation.layout.Box
// import androidx.compose.animation.ExperimentalSharedTransitionApi
// import androidx.compose.animation.SharedTransitionLayout
// import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Entorno envoltorio para manejar transiciones compartidas (Hero Animations)
 * a lo largo de todo el NavHost.
 */
//@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = staticCompositionLocalOf<Any?> { null }

//@OptIn(ExperimentalSharedTransitionApi::class)
val LocalAnimatedVisibilityScope = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }

//@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SharedElementProvider(
    content: @Composable () -> Unit
) {
    Box {
        content()
    }
}
