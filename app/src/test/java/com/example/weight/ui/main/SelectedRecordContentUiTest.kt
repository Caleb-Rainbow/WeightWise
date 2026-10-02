package com.example.weight.ui.main

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertTopPositionInRootIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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

/** 首页 2.0 今日卡的核心信息与设置入口回归。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class SelectedRecordContentUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val record = DailyWeight(
        value = 68.4,
        recordDay = "2026-08-24",
        timestamp = 1_777_000_000_000,
    )

    @Test
    fun `今日卡突出体重与连续打卡并移除独立周期行`() {
        composeRule.setContent {
            AppTheme {
                SelectedRecordContent(
                    record = record,
                    streakInfo = StreakInfo(7, 21, true),
                )
            }
        }

        composeRule.onNodeWithText("今日体重").assertExists()
        composeRule.onNodeWithText("68.4").assertExists()
        composeRule.onNodeWithText("近7天").assertDoesNotExist()
        composeRule.onNodeWithText("连续 7 天").assertExists()
    }

    @Test
    fun `设置图标保持独立可点击入口`() {
        var clicked = false
        composeRule.setContent {
            AppTheme {
                SelectedRecordContent(
                    record = record,
                    onSetting = { clicked = true },
                )
            }
        }

        composeRule.onNodeWithContentDescription("设置").performClick()
        composeRule.runOnIdle { assertTrue(clicked) }
    }

    @Test
    fun `首页标题避开状态栏触控区`() {
        composeRule.setContent {
            val topInset = with(LocalDensity.current) { 24.dp.roundToPx() }
            AppTheme {
                SelectedRecordContent(
                    record = record,
                    statusBarInsets = WindowInsets(0, topInset, 0, 0),
                )
            }
        }

        composeRule.onNodeWithText("WEIGHTWISE / 今日")
            .assertTopPositionInRootIsEqualTo(32.dp)
    }

    @Test
    fun `首页圆形装饰避开状态栏承托层`() {
        var expectedTopPx = 0f
        composeRule.setContent {
            val topInset = with(LocalDensity.current) { 24.dp.roundToPx() }
            expectedTopPx = topInset.toFloat()
            AppTheme {
                SelectedRecordContent(
                    record = record,
                    statusBarInsets = WindowInsets(0, topInset, 0, 0),
                )
            }
        }

        val ringTop = composeRule.onNodeWithTag(HERO_RING_TEST_TAG)
            .fetchSemanticsNode().boundsInRoot.top
        assertEquals(expectedTopPx, ringTop, 0.01f)
    }

}
