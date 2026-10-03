package com.example.weight.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.example.weight.ui.common.FloatingNavigationLayout
import com.example.weight.ui.common.LocalFloatingNavigationInset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w360dp-h640dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FloatingNavigationUiTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `导航悬浮而页面视口仍然占满宿主`() {
        setScreen()
        val host = composeRule.onNodeWithTag("host").fetchSemanticsNode().boundsInRoot
        val page = composeRule.onNodeWithTag("page").fetchSemanticsNode().boundsInRoot
        val navigation = composeRule.onNodeWithTag("navigation").fetchSemanticsNode().boundsInRoot
        assertEquals(host.bottom, page.bottom, 0.5f)
        assertEquals(host.height, page.height, 0.5f)
        assertTrue("导航应覆盖在全屏页面内", navigation.top < page.bottom)
    }

    @Test
    fun `最后一条记录可滚到导航上方完整显示`() {
        setScreen()
        composeRule.onNodeWithTag("page").performScrollToIndex(19)
        val last = composeRule.onNodeWithText("记录 19").fetchSemanticsNode().boundsInRoot
        val navigation = composeRule.onNodeWithTag("navigation").fetchSemanticsNode().boundsInRoot
        assertTrue("最后一项不能被导航盖住", last.bottom <= navigation.top)
        assertTrue(last.top >= 0f)
    }

    @Test
    fun `四个导航标签和独立记体重按钮保留原有动作`() {
        var quickAddCount = 0
        var addWeightCount = 0
        setScreen(onQuickAdd = { quickAddCount++ }, onAddWeight = { addWeightCount++ })
        listOf("首页", "饮食", "记录", "报告").forEach { tab ->
            composeRule.onNodeWithText(tab).performClick().assertIsSelected()
        }
        composeRule.onNodeWithText("饮食").performTouchInput { longClick() }
        composeRule.onNodeWithContentDescription("记体重").performClick()
        composeRule.runOnIdle {
            assertEquals(1, quickAddCount)
            assertEquals(1, addWeightCount)
        }
    }

    private fun setScreen(onQuickAdd: () -> Unit = {}, onAddWeight: () -> Unit = {}) {
        composeRule.setContent {
            MaterialTheme {
                var selectedTab by remember { mutableStateOf("首页") }
                FloatingNavigationLayout(
                    modifier = Modifier.testTag("host"),
                    navigationBar = { hazeState ->
                        MainBottomToolbar(
                            currentTab = selectedTab,
                            hazeState = hazeState,
                            homeKey = "首页", dietKey = "饮食", recordKey = "记录", reportKey = "报告",
                            onSelectHome = { selectedTab = "首页" },
                            onSelectDiet = { selectedTab = "饮食" },
                            onSelectRecord = { selectedTab = "记录" },
                            onSelectReport = { selectedTab = "报告" },
                            onLongPressDiet = onQuickAdd,
                            onAddWeight = onAddWeight,
                            modifier = Modifier.testTag("navigation"),
                        )
                    },
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().testTag("page"),
                        contentPadding = PaddingValues(bottom = LocalFloatingNavigationInset.current + 12.dp),
                    ) {
                        items(20) { index ->
                            Box(Modifier.fillMaxWidth().height(80.dp).padding(16.dp)) {
                                Text("记录 $index")
                            }
                        }
                    }
                }
            }
        }
    }
}
