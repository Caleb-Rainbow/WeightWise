package com.example.weight.ui.diet

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.weight.data.diet.RecognizedFoodItem
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 回归:常用食物+备注组合(dietPrimaryAction=SAVE_THIS_MEAL)下点击「改用文本识别」后,
 * 主按钮必须立即切到「取消识别」——原实现只在 ANALYZE 分支内覆盖 isAnalyzing,
 * 该组合识别中界面零反馈诱发连点,且「保存这餐」在 AI 结果合并前可被误点。
 * qualifiers 拉大视口:主按钮在 LazyColumn 底部,默认小屏下处于视口外不会被组合。
 * application 指定裸 Application:真实 App.onCreate 会初始化 MMKV 原生库,JVM 上不可加载
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w411dp-h1600dp")
class AddTabPagePrimaryActionUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setContent(
        isAnalyzing: Boolean,
        onCancelAnalysis: () -> Unit = {},
    ) {
        composeRule.setContent {
            MaterialTheme {
                AddTabPage(
                    state = AddTabState(
                        recognizedFoods = listOf(
                            RecognizedFoodItem(name = "鸡蛋", estimatedCalories = 78, estimatedGrams = 50),
                        ),
                        isAnalyzing = isAnalyzing,
                    ),
                    todayTotalCalories = 400,
                    recommendedCalories = 1800,
                    noteState = remember { mutableStateOf("还喝了杯牛奶") },
                    hasNote = true,
                    onMealTypeSelected = {},
                    onDateSelected = {},
                    onTakePhoto = {},
                    onPickFromGallery = {},
                    onClearImage = {},
                    onStartAnalysis = {},
                    onCancelAnalysis = onCancelAnalysis,
                    onDiscardAnalysis = {},
                    onQuickAddFood = {},
                    onEditFood = {},
                    onRemoveFood = {},
                    onClearFoods = {},
                    onSave = {},
                    listState = rememberLazyListState(),
                )
            }
        }
    }

    @Test
    fun `识别中覆盖保存这餐分支_显示取消识别`() {
        setContent(isAnalyzing = true)
        composeRule.onNodeWithText("取消识别").assertExists()
        composeRule.onNodeWithText("识别中,通常需要 10–20 秒 · 可继续改餐次或备注").assertExists()
        composeRule.onAllNodesWithText("保存这餐 · 78 kcal").assertCountEquals(0)
        composeRule.onAllNodesWithText("改用文本识别").assertCountEquals(0)
    }

    @Test
    fun `识别中点击主按钮触发取消`() {
        var cancelled = false
        setContent(isAnalyzing = true, onCancelAnalysis = { cancelled = true })
        composeRule.onNodeWithText("取消识别").performClick()
        composeRule.runOnIdle { assertTrue(cancelled) }
    }

    @Test
    fun `空闲时保持快速保存路径`() {
        setContent(isAnalyzing = false)
        composeRule.onNodeWithText("保存这餐 · 78 kcal").assertExists()
        composeRule.onNodeWithText("改用文本识别").assertExists()
        composeRule.onAllNodesWithText("取消识别").assertCountEquals(0)
    }
}
