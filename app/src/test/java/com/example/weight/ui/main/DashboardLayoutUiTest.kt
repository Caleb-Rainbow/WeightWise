package com.example.weight.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.weight.data.record.DailyWeight
import com.example.weight.ui.theme.AppTheme
import com.example.weight.util.StreakInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import android.graphics.Bitmap
import java.io.File

@RunWith(RobolectricTestRunner::class)
// Haze's native screenshot renderer needs SDK 35+ for its shader and edge handling.
@Config(sdk = [35], application = android.app.Application::class, qualifiers = "w360dp-h640dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DashboardLayoutUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val records = (0..6).map { index ->
        DailyWeight(
            value = 77.2 - index * 0.2,
            recordDay = "2026-09-${24 + index}",
            timestamp = 1_790_200_000_000L + index * 86_400_000L,
        )
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `观察周期位于趋势标题右侧且能够切换`() {
        var selected = StatisticsScope.LAST_7DAYS
        composeRule.setContent {
            var scope by remember { mutableStateOf(selected) }
            AppTheme {
                Column {
                    DashboardTrendSection(
                        currentScopeDataList = records,
                        selectedScope = scope,
                        onScopeSelected = { scope = it; selected = it },
                        maxWeight = 79.0,
                        minWeight = 71.0,
                        targetWeight = 72.0,
                        chartHeight = 180.dp,
                        onMarkerClick = {},
                    )
                }
            }
        }
        val title = composeRule.onNodeWithText("趋势轨迹").fetchSemanticsNode().boundsInRoot
        val anchor = composeRule.onNodeWithTag(SCOPE_MENU_ANCHOR_TEST_TAG, useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertTrue(anchor.left > title.right)
        assertTrue(anchor.top < title.bottom && anchor.bottom > title.top)
        composeRule.onNodeWithText("观察周期").assertDoesNotExist()
        composeRule.onNodeWithText("近7天").assertHasClickAction().performClick()
        composeRule.waitUntilAtLeastOneExists(hasText("近14天"), timeoutMillis = 5_000)
        composeRule.onNodeWithText("近14天").performClick()
        composeRule.runOnIdle { assertEquals(StatisticsScope.LAST_14DAYS, selected) }
    }

    @Test
    fun `小屏首屏包含完整趋势图与说明`() {
        setDashboard(viewportHeight = 552.dp)
        assertTrendFitsViewport()
        composeRule.onNodeWithText("总目标进度").assertExists()
        composeRule.onNodeWithText("50%").assertExists()
        composeRule.onNodeWithText("还差 4.0 kg").assertExists()
        composeRule.onNodeWithText("阶段", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("记录体重").assertDoesNotExist()
        composeRule.onNodeWithText("记一餐").assertDoesNotExist()
        composeRule.onNodeWithText("展开依据与图例").performClick()
        composeRule.onNodeWithText("记录覆盖约", substring = true).assertExists()
        composeRule.onNodeWithText("实线为平滑趋势", substring = true).assertExists()
        composeRule.onNodeWithText("收起依据与图例").performClick()
        assertTrendFitsViewport()
    }

    @Test
    @Config(qualifiers = "w411dp-h800dp")
    fun `总目标补充腰围和体脂后常规屏幕仍完整展示趋势`() {
        setDashboard(viewportHeight = 712.dp, withBodyGoals = true)
        assertTrendFitsViewport()
        composeRule.onNodeWithText("腰围 80.0 → 75.0 cm  ·  体脂 25.0 → 20.0%").assertExists()
    }

    private fun setDashboard(viewportHeight: Dp, withBodyGoals: Boolean = false) {
        composeRule.setContent {
            val topInset = with(LocalDensity.current) { 24.dp.roundToPx() }
            AppTheme {
                Box(modifier = Modifier.height(viewportHeight).fillMaxWidth().testTag("viewport")) {
                    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        DashboardLayout(
                            viewportHeight = viewportHeight,
                            heroContent = { heroModifier ->
                                Surface(
                                    modifier = heroModifier.fillMaxWidth(),
                                    color = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                ) {
                                    Column {
                                        SelectedRecordContent(
                                            record = records.last(),
                                            streakInfo = StreakInfo(7, 21, true),
                                            statusBarInsets = WindowInsets(0, topInset, 0, 0),
                                        )
                                        GoalProgressSummary(
                                            startWeight = 80.0,
                                            currentWeight = 76.0,
                                            targetWeight = 72.0,
                                            progress = 0.5f,
                                            goalReached = false,
                                            plannedDays = 56,
                                            remainingDays = 48,
                                            currentWaistCm = if (withBodyGoals) 80.0 else 0.0,
                                            targetWaistCm = if (withBodyGoals) 75.0 else 0.0,
                                            currentBodyFatPercent = if (withBodyGoals) 25.0 else 0.0,
                                            targetBodyFatPercent = if (withBodyGoals) 20.0 else 0.0,
                                            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 10.dp)
                                                .fillMaxWidth(),
                                        )
                                    }
                                }
                            },
                            trendContent = { height ->
                                DashboardTrendSection(
                                    currentScopeDataList = records,
                                    selectedScope = StatisticsScope.LAST_7DAYS,
                                    onScopeSelected = {},
                                    maxWeight = 79.0,
                                    minWeight = 71.0,
                                    targetWeight = 72.0,
                                    chartHeight = height,
                                    onMarkerClick = {},
                                )
                            },
                            statsContent = {},
                            bmiContent = {},
                        )
                    }
                }
            }
        }
    }

    private fun assertTrendFitsViewport() {
        composeRule.waitForIdle()
        val viewport = composeRule.onNodeWithTag("viewport").fetchSemanticsNode()
        val trend = composeRule.onNodeWithTag(TREND_CHART_TEST_TAG).fetchSemanticsNode()
        // boundsInRoot 会裁到父容器边缘；使用布局尺寸，避免把被遮住的卡片误判为完整可见。
        val viewportBottom = viewport.positionInRoot.y + viewport.size.height
        val trendBottom = trend.positionInRoot.y + trend.size.height
        assertTrue("趋势卡底部 $trendBottom 应在首屏底部 $viewportBottom 内", trendBottom <= viewportBottom)
        val preview = File("build/reports/tests/testDebugUnitTest/dashboard-${viewport.size.width}.png")
        preview.parentFile?.mkdirs()
        preview.outputStream().use {
            composeRule.onNodeWithTag("viewport").captureToImage().asAndroidBitmap()
                .compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
