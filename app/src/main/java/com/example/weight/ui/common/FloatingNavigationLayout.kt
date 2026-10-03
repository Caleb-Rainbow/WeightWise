package com.example.weight.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import com.example.weight.ui.theme.SurfaceEffect
import androidx.compose.runtime.remember

/** Only scrollable content's trailing space uses this inset; the viewport stays full screen. */
internal val LocalFloatingNavigationInset = compositionLocalOf { 0.dp }

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun FloatingNavigationLayout(
    navigationBar: @Composable (HazeState) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHost: @Composable () -> Unit = {},
    effect: SurfaceEffect = SurfaceEffect.BLUR,
    content: @Composable () -> Unit,
) {
    val hazeState = rememberHazeState()
    val imeVisible = WindowInsets.isImeVisible
    val context = remember(hazeState, effect) { AppMaterialContext(hazeState, effect) }
    CompositionLocalProvider(
        LocalAppMaterialContext provides context,
        LocalOverlayMaterialContext provides context,
    ) {
    Scaffold(
        modifier = modifier.fillMaxSize().imePadding(),
        contentWindowInsets = WindowInsets(0),
        snackbarHost = snackbarHost,
        bottomBar = { if (!imeVisible) navigationBar(hazeState) },
    ) { overlayPadding ->
        CompositionLocalProvider(
            LocalFloatingNavigationInset provides overlayPadding.calculateBottomPadding(),
        ) {
            // Scaffold's padding is a scrolling clearance, never a reduction of the page bounds.
            // Capture the page only, so glass never samples itself or the snackbar above it.
            Box(
                Modifier
                    .fillMaxSize()
                    .hazeSource(hazeState)
                    .background(MaterialTheme.colorScheme.background),
            ) {
                content()
            }
        }
    }
    }
}
