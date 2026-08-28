package com.example.weight.ui.report

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.weight.util.WeeklyControlEngine
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * 决策卡 UI 回归（Robolectric）：三态渲染、null 占位不崩、依据展开交互、
 * TalkBack 三段连读语义（评审 2A/5A 落地验证）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class WeeklyDecisionCardUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun math(
        slope: Double? = -0.18,
        avgIntake: Int? = 2280,
        baseline: Int? = 1870,
        adaptive: Int? = 2050,
        tdeeState: WeeklyControlEngine.TdeeState = WeeklyControlEngine.TdeeState.READY,
        windowDays: Int = 42,
        weightDays: Int = 40,
        weeks: List<WeeklyControlEngine.WeekStat> = listOf(
            WeeklyControlEngine.WeekStat(LocalDate.of(2026, 8, 17), 7, 1923, true, null),
            WeeklyControlEngine.WeekStat(LocalDate.of(2026, 8, 10), 4, 1910, false, WeeklyControlEngine.ExcludeReason.LOW_COVERAGE),
        ),
        includedWeekCount: Int = 1,
        baselineFromAdaptive: Boolean = true,
    ) = WeeklyControlEngine.ControlMath(
        windowDays = windowDays,
        weightDays = weightDays,
        slopeKgPerWeek = slope,
        slopeLowKgPerWeek = slope?.let { it - 0.12 },
        slopeHighKgPerWeek = slope?.let { it + 0.12 },
        slopeConfidence = WeeklyControlEngine.SlopeConfidence.OK,
        weekStats = weeks,
        includedWeekCount = includedWeekCount,
        avgIntakeKcal = avgIntake,
        adaptiveTdeeKcal = adaptive,
        tdeeState = tdeeState,
        staticTdeeKcal = 2370,
        tdeeDeviationAlert = false,
        baselineKcal = baseline,
        baselineFromAdaptive = baselineFromAdaptive,
        goalBaseWeightKg = 80.0,
        dailyDeficitKcal = 500,
    )

    private fun render(result: WeeklyControlEngine.WeeklyControlResult?, target: Double = 75.0) {
        composeRule.setContent {
            MaterialTheme(colorScheme = lightColorScheme()) {
                WeeklyDecisionCard(result = result, targetWeightKg = target)
            }
        }
    }

    @Test
    fun `null结果渲染固定占位不崩溃`() {
        render(null)
        composeRule.onAllNodesWithText("WEEKLY DECISION").fetchSemanticsNodes().let {
            assertEquals("占位态不应渲染卡片内容", 0, it.size)
        }
    }

    @Test
    fun `微调态显示朱砂动词与超预算百分比`() {
        val r = WeeklyControlEngine.WeeklyControlResult(
            decision = WeeklyControlEngine.ControlDecision.Adjust(
                suggestion = WeeklyControlEngine.AdjustSuggestion.CutFood(
                    WeeklyControlEngine.IndulgentMeal("深夜泡面", 4, 550),
                ),
                excessKcalPerDay = 410,
            ),
            math = math(),
        )
        render(r)
        composeRule.onNodeWithText("减掉：深夜泡面").assertExists()
        composeRule.onNodeWithText("近 60 天 4 餐 · 放纵频次最高", substring = true).assertExists()
    }

    @Test
    fun `展开依据显示算式四块与被排除周明细`() {
        val r = WeeklyControlEngine.WeeklyControlResult(
            decision = WeeklyControlEngine.ControlDecision.Adjust(
                suggestion = WeeklyControlEngine.AdjustSuggestion.CutFood(
                    WeeklyControlEngine.IndulgentMeal("深夜泡面", 4, 550),
                ),
                excessKcalPerDay = 410,
            ),
            math = math(),
        )
        render(r)
        composeRule.onNodeWithText("查看依据 ▾").performClick()
        composeRule.onNodeWithText("周均摄入").assertExists()
        composeRule.onNodeWithText("决策基准").assertExists()
        composeRule.onNodeWithText("体重斜率").assertExists()
        composeRule.onNodeWithText("护栏").assertExists()
        // 被排除周逐条列明（不静默隐藏）
        composeRule.onNodeWithText("8/10 周 · 覆盖不足（记录 4/7 天，日均 1910 kcal）", substring = true).assertExists()
    }

    @Test
    fun `数据不足态显示缺口明细且无展开入口`() {
        val r = WeeklyControlEngine.WeeklyControlResult(
            decision = WeeklyControlEngine.ControlDecision.Insufficient(
                WeeklyControlEngine.InsufficientKind.WEIGHT_DAYS,
                "还需 3 个体重日",
            ),
            math = math(weightDays = 11, windowDays = 21, slope = null, avgIntake = null),
        )
        render(r)
        composeRule.onNodeWithText("本周先补记录").assertExists()
        composeRule.onNodeWithText("还需 3 个体重日").assertExists()
        composeRule.onNodeWithText("基于 21 天").assertExists()
        composeRule.onAllNodesWithText("查看依据 ▾").fetchSemanticsNodes().let {
            assertEquals("数据不足态无依据可展开", 0, it.size)
        }
    }

    @Test
    fun `保持态动词与三段连读语义`() {
        val r = WeeklyControlEngine.WeeklyControlResult(
            decision = WeeklyControlEngine.ControlDecision.Hold(WeeklyControlEngine.HoldKind.ON_TRACK_SLOPE),
            math = math(slope = -0.55),
        )
        render(r)
        composeRule.onNodeWithText("继续保持").assertExists()
        // 评审 5A：TalkBack 三段连读「本周决策+动词+副文案」
        composeRule.onNodeWithContentDescription("本周决策，继续保持，下降 0.55 kg/周，达到目标节奏")
            .assertExists()
    }
}
