package com.example.weight.ui.common

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.example.weight.ui.theme.SurfaceEffect
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeSourceRetention
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.hazeGlass
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

@Stable
internal data class AppMaterialContext(val state: HazeState, val effect: SurfaceEffect)

/** In-window chrome reads the screen's pure scrolling content; popups read the complete parent window. */
internal val LocalAppMaterialContext = compositionLocalOf<AppMaterialContext?> { null }
internal val LocalOverlayMaterialContext = compositionLocalOf<AppMaterialContext?> { null }
internal val LocalAppTopInset = compositionLocalOf { 0.dp }

internal enum class AppMaterialProfile { Control, Chrome, Readable }

@OptIn(ExperimentalHazeApi::class)
@Composable
internal fun Modifier.appMaterial(
    shape: Shape = RoundedCornerShape(20.dp),
    profile: AppMaterialProfile = AppMaterialProfile.Control,
    color: Color = MaterialTheme.colorScheme.surface,
    context: AppMaterialContext? = LocalAppMaterialContext.current,
): Modifier {
    if (context == null) return clip(shape).background(color)
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val blurTint = when (profile) {
        AppMaterialProfile.Control -> if (dark) 0.46f else 0.32f
        AppMaterialProfile.Chrome -> 0.78f
        AppMaterialProfile.Readable -> if (dark) 0.86f else 0.80f
    }
    val glassTint = when (profile) {
        AppMaterialProfile.Control -> if (dark) 0.28f else 0.16f
        AppMaterialProfile.Chrome -> 0.78f
        AppMaterialProfile.Readable -> if (dark) 0.82f else 0.74f
    }
    val roundedShape = shape as? RoundedCornerShape ?: RoundedCornerShape(0.dp)
    val glassStyle = remember(color, profile, roundedShape, dark) {
        val preset = if (profile == AppMaterialProfile.Control) GlassStyle.clear else GlassStyle.regular
        preset.then {
            shape(roundedShape)
            backgroundColor(color)
            tint(color.copy(alpha = glassTint))
            specularIntensity(if (dark) 0.42f else 0.55f)
            ambientResponse(if (dark) 0.22f else 0.35f)
            // Match forced app appearance even when Android's window appearance differs.
            whitePoint(if (dark) -0.18f else if (profile == AppMaterialProfile.Control) 0.17f else 0f)
            contrast(if (dark) 0.12f else 0.08f)
            lightPosition(Alignment.TopStart)
        }
    }
    val blurStyle = remember(color, profile, dark) {
        HazeBlurStyle {
            blurEnabled(true)
            backgroundColor(color)
            blurRadius(10.dp)
            // Shared chrome uses the blur/color-filter path without an extra noise shader per layer.
            noiseFactor(0f)
            colorEffects(listOf(HazeColorEffect.tint(color.copy(alpha = blurTint))))
        }
    }
    val input = HazeInput.Sources(context.state, retention = HazeSourceRetention.ClearWhenUnavailable)
    return clip(shape)
        .then(
            if (context.effect.forSdk(Build.VERSION.SDK_INT) == SurfaceEffect.GLASS) {
                Modifier.hazeGlass(input, glassStyle, performanceMode = HazePerformanceMode.Balanced)
            } else {
                Modifier.hazeBlur(input, blurStyle, performanceMode = HazePerformanceMode.Balanced)
            },
        )
        .border(
            0.75.dp,
            Brush.verticalGradient(
                listOf(Color.White.copy(alpha = if (dark) 0.22f else 0.58f), Color.White.copy(alpha = 0.04f)),
            ),
            shape,
        )
}

/** Full-screen source and sibling chrome: never capture an effect into its own input. */
@Composable
internal fun AppScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val state = rememberHazeState()
    val effect = LocalAppMaterialContext.current?.effect ?: SurfaceEffect.BLUR
    val context = remember(state, effect) { AppMaterialContext(state, effect) }
    CompositionLocalProvider(LocalAppMaterialContext provides context) {
        Scaffold(
            modifier = modifier,
            contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
            topBar = topBar,
        ) { chromePadding ->
            CompositionLocalProvider(LocalAppTopInset provides chromePadding.calculateTopPadding()) {
                Box(
                    Modifier.fillMaxSize().hazeSource(state).background(MaterialTheme.colorScheme.background),
                ) {
                    content(chromePadding)
                }
            }
        }
    }
}
