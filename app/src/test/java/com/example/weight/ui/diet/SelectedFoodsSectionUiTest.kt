package com.example.weight.ui.diet

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.example.weight.data.diet.RecognizedFoodItem

/**
 * T10:首个 Compose UI 测试(Robolectric + createComposeRule 基建验证)。
 * 覆盖 SelectedFoodsSection 的汇总条文案与移除回调(Q2A 共享组件)。
 * application 指定裸 Application:真实 App.onCreate 会初始化 MMKV 原生库,JVM 上不可加载
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class SelectedFoodsSectionUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun foods() = listOf(
        RecognizedFoodItem(name = "苹果", estimatedCalories = 95, estimatedGrams = 180),
        RecognizedFoodItem(name = "鸡胸肉沙拉", estimatedCalories = 320, estimatedGrams = 320),
    )

    @Test
    fun `汇总条显示项数与合计`() {
        composeRule.setContent {
            MaterialTheme { SelectedFoodsSection(foods = foods(), onEdit = {}, onRemove = {}, onClear = null) }
        }
        composeRule.onNodeWithText("已选 2 项 · 共 415 kcal").assertExists()
        composeRule.onNodeWithText("苹果").assertExists()
    }

    @Test
    fun `点击移除回调对应索引`() {
        var removedIndex = -1
        composeRule.setContent {
            MaterialTheme {
                SelectedFoodsSection(foods = foods(), onEdit = {}, onRemove = { removedIndex = it }, onClear = null)
            }
        }
        composeRule.onNodeWithContentDescription("移除苹果").performClick()
        composeRule.runOnIdle { assertEquals(0, removedIndex) }
    }

    @Test
    fun `每行都有编辑与移除入口`() {
        composeRule.setContent {
            MaterialTheme { SelectedFoodsSection(foods = foods(), onEdit = {}, onRemove = {}, onClear = null) }
        }
        composeRule.onAllNodesWithContentDescription("编辑苹果").assertCountEquals(1)
        composeRule.onAllNodesWithContentDescription("移除鸡胸肉沙拉").assertCountEquals(1)
    }
}
