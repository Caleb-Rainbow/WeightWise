package com.example.weight

import android.app.Application
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 系统栏策略回归：深色沉浸页头必须配透明状态栏和浅色图标。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SystemBarsTest {

    @Suppress("DEPRECATION")
    @Test
    fun `状态栏透明且使用浅色图标`() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()

        activity.enableWeightWiseEdgeToEdge()

        val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        assertEquals(Color.TRANSPARENT, activity.window.statusBarColor)
        assertFalse(controller.isAppearanceLightStatusBars)
    }

    @Test
    fun `状态栏图标跟随页头内容色对比度`() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)

        activity.window.syncStatusBarIconContrast(onPrimaryLuminance = 1f)
        assertFalse("深色页头上的浅色内容色应对应浅色系统图标", controller.isAppearanceLightStatusBars)

        activity.window.syncStatusBarIconContrast(onPrimaryLuminance = 0f)
        assertTrue("浅色页头上的深色内容色应对应深色系统图标", controller.isAppearanceLightStatusBars)
    }
}
