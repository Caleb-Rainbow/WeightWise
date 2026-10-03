package com.example.weight.ui.main

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.example.weight.ui.common.FloatingNavigationLayout
import com.example.weight.ui.theme.SurfaceEffect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import kotlin.math.abs

/** Hardware A/B test: Clear Glass and 10dp Blur sample the page and render distinct materials. */
class FloatingNavigationGlassDeviceTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun lightGlassAndBlurCanBeComparedLive() = verifyGlass(dark = false)
    @Test fun darkGlassAndBlurCanBeComparedLive() = verifyGlass(dark = true)

    private fun verifyGlass(dark: Boolean) {
        assumeTrue(Build.VERSION.SDK_INT >= 33)
        val alternateBackground = mutableStateOf(false)
        val surfaceEffect = mutableStateOf(SurfaceEffect.GLASS)
        composeRule.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale = 1f)) {
                MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(width = 360.dp, height = 640.dp)) {
                            FloatingNavigationLayout(
                                modifier = Modifier.testTag("host"),
                                effect = surfaceEffect.value,
                                navigationBar = { state ->
                                    MainBottomToolbar(
                                        currentTab = "home", hazeState = state,
                                        effect = surfaceEffect.value,
                                        homeKey = "home", dietKey = "diet", recordKey = "record", reportKey = "report",
                                        onSelectHome = {}, onSelectDiet = {}, onSelectRecord = {}, onSelectReport = {},
                                        onLongPressDiet = {}, onAddWeight = {},
                                        modifier = Modifier.testTag("navigation"),
                                    )
                                },
                            ) {
                                Canvas(Modifier.fillMaxSize().testTag("page")) {
                                    // High frequency stripes show whether the material really diffuses its input.
                                    val stripe = 4.dp.toPx()
                                    var x = 0f
                                    var index = 0
                                    while (x < size.width) {
                                        drawRect(
                                            color = when {
                                                alternateBackground.value -> Color(0xFFEA4545)
                                                index % 2 == 0 -> Color(0xFF146BCA)
                                                else -> Color(0xFFF4B064)
                                            },
                                            topLeft = Offset(x, 0f), size = Size(stripe, size.height),
                                        )
                                        x += stripe
                                        index++
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()
        val host = composeRule.onNodeWithTag("host").fetchSemanticsNode().boundsInRoot
        val page = composeRule.onNodeWithTag("page").fetchSemanticsNode().boundsInRoot
        assertEquals(host.bottom, page.bottom, 0.5f)
        val image = composeRule.onNodeWithTag("host").captureToImage().asAndroidBitmap()
        val density = image.width / 360f
        val navigation = composeRule.onNodeWithTag("navigation").fetchSemanticsNode().boundsInRoot
        // A band under the material's top rim avoids foreground icons, labels and the selected tab.
        val glassY = (navigation.top - host.top + 18f * density).toInt()
        val plainY = (navigation.top - host.top - 20f * density).toInt()
        val start = (95f * density).toInt()
        val end = (225f * density).toInt()
        val plainVariation = variation(image, plainY, start, end)
        val glassVariation = variation(image, glassY, start, end)
        assertTrue("The source stripes must have measurable contrast", plainVariation > 10f)

        composeRule.runOnIdle { surfaceEffect.value = SurfaceEffect.BLUR }
        composeRule.waitForIdle()
        val blurImage = composeRule.onNodeWithTag("host").captureToImage().asAndroidBitmap()
        val blurVariation = variation(blurImage, glassY, start, end)
        assertTrue(
            "Blur must diffuse the source, plain=$plainVariation blur=$blurVariation",
            blurVariation < plainVariation * 0.20f,
        )
        assertTrue(
            "Clear Glass must preserve more detail than Blur, glass=$glassVariation blur=$blurVariation",
            glassVariation > blurVariation + 1f,
        )
        composeRule.runOnIdle { surfaceEffect.value = SurfaceEffect.GLASS }
        composeRule.waitForIdle()
        // An opaque, fixed-color pill would also remove stripes. It must follow changed source colors.
        composeRule.runOnIdle { alternateBackground.value = true }
        composeRule.waitForIdle()
        val changed = composeRule.onNodeWithTag("host").captureToImage().asAndroidBitmap()
        val sampleX = (160f * density).toInt()
        val before = image.getPixel(sampleX, glassY)
        val after = changed.getPixel(sampleX, glassY)
        val colorChange = abs(android.graphics.Color.red(before) - android.graphics.Color.red(after)) +
            abs(android.graphics.Color.blue(before) - android.graphics.Color.blue(after))
        assertTrue("Glass must sample the current page instead of drawing a fixed fill", colorChange > 25)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.getExternalFilesDir("navigation-preview"), if (dark) "glass-dark.png" else "glass-light.png")
        file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val blurFile = File(context.getExternalFilesDir("navigation-preview"), if (dark) "blur-dark.png" else "blur-light.png")
        blurFile.outputStream().use { blurImage.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun variation(bitmap: Bitmap, y: Int, start: Int, end: Int): Float {
        var total = 0f
        for (x in start until end) {
            val a = bitmap.getPixel(x, y)
            val b = bitmap.getPixel(x + 1, y)
            total += abs(android.graphics.Color.red(a) - android.graphics.Color.red(b))
            total += abs(android.graphics.Color.blue(a) - android.graphics.Color.blue(b))
        }
        return total / (end - start)
    }
}
