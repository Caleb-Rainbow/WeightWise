package com.example.weight.ui.common

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import com.example.weight.ui.main.MainBottomToolbar
import com.example.weight.ui.theme.SurfaceEffect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

/** Real-window coverage for screen capture ownership and dialog/sheet/popup alignment. */
class AppMaterialsDeviceTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun topChromeKeepsAFullScreenViewportAndTheLastItemAccessible() {
        composeRule.setContent {
            MaterialTheme {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(360.dp, 640.dp)) {
                        FloatingNavigationLayout(
                            modifier = Modifier.testTag("host"),
                            navigationBar = { state ->
                                MainBottomToolbar(
                                    currentTab = "home", hazeState = state,
                                    homeKey = "home", dietKey = "diet", recordKey = "record", reportKey = "report",
                                    onSelectHome = {}, onSelectDiet = {}, onSelectRecord = {}, onSelectReport = {},
                                    onLongPressDiet = {}, onAddWeight = {}, modifier = Modifier.testTag("navigation"),
                                )
                            },
                        ) {
                            AppScaffold(topBar = {
                                MyTopBar(title = "材质测试", goBack = {}, modifier = Modifier.testTag("chrome"))
                            }) { insets ->
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize().testTag("page"),
                                    contentPadding = PaddingValues(
                                        top = insets.calculateTopPadding() + 12.dp,
                                        bottom = LocalFloatingNavigationInset.current + 12.dp,
                                    ),
                                ) {
                                    items(20) { index ->
                                        Text("记录 $index", Modifier.fillMaxWidth().height(80.dp).padding(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        val host = composeRule.onNodeWithTag("host").fetchSemanticsNode().boundsInRoot
        val page = composeRule.onNodeWithTag("page").fetchSemanticsNode().boundsInRoot
        val chrome = composeRule.onNodeWithTag("chrome").fetchSemanticsNode().boundsInRoot
        val first = composeRule.onNodeWithText("记录 0").fetchSemanticsNode().boundsInRoot
        assertEquals(host.top, page.top, 0.5f)
        assertEquals(host.bottom, page.bottom, 0.5f)
        assertTrue(first.top >= chrome.bottom)
        composeRule.onNodeWithTag("page").performScrollToIndex(19)
        val last = composeRule.onNodeWithText("记录 19").fetchSemanticsNode().boundsInRoot
        val navigation = composeRule.onNodeWithTag("navigation").fetchSemanticsNode().boundsInRoot
        assertTrue(last.bottom <= navigation.top)
    }

    @Test fun dialogsFollowTheirParentWindowInBothModes() = verifyOverlay(Overlay.Dialog)
    @Test fun sheetsFollowTheirParentWindowInBothModes() = verifyOverlay(Overlay.Sheet)
    @Test fun menusFollowTheirParentWindowInBothModes() = verifyOverlay(Overlay.Menu)

    @OptIn(ExperimentalMaterial3Api::class)
    @Test fun nestedDialogsCaptureTheSheetBehindThem() {
        val blue = mutableStateOf(true)
        composeRule.setContent {
            MaterialTheme {
                FloatingNavigationLayout(navigationBar = {}) {
                    Canvas(Modifier.fillMaxSize()) { drawRect(Color.Gray) }
                    AppModalBottomSheet(
                        onDismissRequest = {},
                        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    ) {
                        Canvas(Modifier.fillMaxWidth().height(1000.dp)) {
                            drawRect(if (blue.value) Color(0xFF146BCA) else Color(0xFFF4B064))
                        }
                        AppAlertDialog(
                            onDismissRequest = {}, modifier = Modifier.testTag("nested"),
                            title = { Text("删除记录") }, text = { Text("嵌套窗口采样检查") },
                            confirmButton = { TextButton(onClick = {}) { Text("确认") } },
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
        val before = composeRule.onNodeWithTag("nested").captureToImage().asAndroidBitmap()
        composeRule.runOnIdle { blue.value = false }
        composeRule.waitForIdle()
        val after = composeRule.onNodeWithTag("nested").captureToImage().asAndroidBitmap()
        assertTrue(
            "Nested dialog must sample its sheet instead of the unchanged gray main window",
            difference(before, after, before.width / 3, (before.width * 0.045f).toInt()) > 12,
        )
    }

    private enum class Overlay { Dialog, Sheet, Menu }

    @OptIn(ExperimentalMaterial3Api::class)
    private fun verifyOverlay(overlay: Overlay) {
        val effect = mutableStateOf(SurfaceEffect.BLUR)
        val blue = mutableStateOf(true)
        composeRule.setContent {
            MaterialTheme {
                FloatingNavigationLayout(navigationBar = {}, effect = effect.value) {
                    AppScaffold(topBar = { MyTopBar(title = "窗口测试", goBack = {}) }) {
                        Canvas(Modifier.fillMaxSize()) {
                            drawRect(if (blue.value) Color(0xFF146BCA) else Color(0xFFF4B064))
                        }
                    }
                    when (overlay) {
                        Overlay.Dialog -> AppAlertDialog(
                            modifier = Modifier.testTag("overlay"), onDismissRequest = {},
                            title = { Text("编辑记录") }, text = { Text("窗口材质对齐检查") },
                            confirmButton = { TextButton(onClick = {}) { Text("确认") } },
                        )
                        Overlay.Sheet -> AppModalBottomSheet(
                            modifier = Modifier.testTag("overlay"), onDismissRequest = {},
                            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                        ) {
                            Column(Modifier.fillMaxWidth().height(240.dp).padding(24.dp)) {
                                Text("编辑面板")
                            }
                        }
                        Overlay.Menu -> Box(Modifier.padding(top = 180.dp, start = 48.dp)) {
                            AppDropdownMenu(
                                expanded = true, onDismissRequest = {}, modifier = Modifier.testTag("overlay"),
                            ) { Text("观察周期", Modifier.padding(24.dp)) }
                        }
                    }
                }
            }
        }
        for (mode in SurfaceEffect.entries) {
            composeRule.runOnIdle { effect.value = mode; blue.value = true }
            composeRule.waitForIdle()
            val before = composeRule.onNodeWithTag("overlay").captureToImage().asAndroidBitmap()
            composeRule.runOnIdle { blue.value = false }
            composeRule.waitForIdle()
            val after = composeRule.onNodeWithTag("overlay").captureToImage().asAndroidBitmap()
            val x = before.width / 3
            val y = (before.width * 0.045f).toInt().coerceIn(2, before.height - 1)
            assertTrue("$overlay in $mode must sample changed parent pixels", difference(before, after, x, y) > 12)
        }
    }

    private fun difference(a: Bitmap, b: Bitmap, x: Int, y: Int): Int {
        val first = a.getPixel(x, y)
        val second = b.getPixel(x, y)
        return abs(android.graphics.Color.red(first) - android.graphics.Color.red(second)) +
            abs(android.graphics.Color.blue(first) - android.graphics.Color.blue(second))
    }
}
