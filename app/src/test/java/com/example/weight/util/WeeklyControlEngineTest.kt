package com.example.weight.util

import com.example.weight.data.diet.DailyCalories
import com.example.weight.data.record.DailyWeight
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.abs
import kotlin.random.Random

/**
 * 每周控制回路引擎单测。测试计划见 ~/.gstack/.../eng-review-test-plan（2026-08-28）。
 * today 固定为 2026-08-28（周五）：最后一个完整周为 08-17（周一）那周，
 * 42 天窗口起点 07-18（周六）为残缺首周——天然覆盖"首周丢弃"用例。
 */
class WeeklyControlEngineTest {

    private val today: LocalDate = LocalDate.of(2026, 8, 28) // 周五
    private val windowStart: LocalDate = today.minusDays(41) // 07-18 周六

    private val profile = WeeklyControlEngine.ControlProfile(
        gender = Gender.MALE,
        age = 30,
        heightCm = 175.0,
        activityLevel = ActivityLevel.MODERATE,
    )

    // ------------------------------------------------------------- 数据生成

    /** 均匀生成窗口内每天体重：真值斜率 + 高斯噪声（std=noiseKg，java.util.Random 可复现） */
    private fun genWeights(
        startKg: Double,
        slopeKgPerWeek: Double,
        seed: Int = 42,
        noiseKg: Double = 0.8,
        from: LocalDate = windowStart,
        to: LocalDate = today,
    ): List<DailyWeight> = buildList {
        val rnd = java.util.Random(seed.toLong())
        var d = from
        while (!d.isAfter(to)) {
            val trend = startKg + slopeKgPerWeek / 7.0 * (d.toEpochDay() - from.toEpochDay())
            add(DailyWeight(value = trend + rnd.nextGaussian() * noiseKg, recordDay = d.toString(), timestamp = d.toEpochDay() * DAY_MS))
            d = d.plusDays(1)
        }
    }

    /** 生成窗口内每日热量：全部记录（coverage=1.0），指定每日均值 */
    private fun genCalories(kcalPerDay: Int, from: LocalDate = windowStart, to: LocalDate = today): List<DailyCalories> =
        buildList {
            var d = from
            while (!d.isAfter(to)) {
                add(DailyCalories(date = d.toString(), calories = kcalPerDay))
                d = d.plusDays(1)
            }
        }

    private fun evaluate(
        weights: List<DailyWeight>,
        calories: List<DailyCalories>,
        targetWeightKg: Double = 70.0,
        weeklyTargetChangeKg: Double = 0.5,
        profileArg: WeeklyControlEngine.ControlProfile? = null,
        staticRecommendedIntake: Int? = 1900,
        indulgent: List<WeeklyControlEngine.IndulgentMeal> = emptyList(),
    ): WeeklyControlEngine.WeeklyControlResult {
        val effectiveProfile = profileArg ?: profile
        return WeeklyControlEngine.evaluate(
        WeeklyControlEngine.WeeklyControlInput(
            today = today,
            dailyWeights = weights,
            dailyCalories = calories,
            targetWeightKg = targetWeightKg,
            weeklyTargetChangeKg = weeklyTargetChangeKg,
            profile = effectiveProfile,
            staticRecommendedIntake = staticRecommendedIntake,
            indulgentMeals = indulgent,
        ),
        )
    }

    // ------------------------------------------------------------- 精度（Success Criteria）

    @Test
    fun `合成6周数据TDEE反推误差95分位小于15百分比`() {
        // 真值：slope=-0.2kg/周、摄入1800 → TDEE=1800+0.2/7*7000=2000；
        // 真 ±0.8kg std 高斯噪声 × 20 种子（最大值≈95 分位，对齐 Success Criteria 口径）
        val errors = (1..20).map { seed ->
            val r = evaluate(
                weights = genWeights(startKg = 75.0, slopeKgPerWeek = -0.2, seed = seed),
                calories = genCalories(1800),
            )
            assertEquals(WeeklyControlEngine.TdeeState.READY, r.math.tdeeState)
            val tdee = requireNotNull(r.math.adaptiveTdeeKcal)
            abs(tdee - 2000).toDouble() / 2000
        }
        assertTrue("反推相对误差 ${errors.map { (it * 100).toInt() }}%", errors.all { it < 0.15 })
    }

    @Test
    fun `高斜率用例方向正确且置信带覆盖真值`() {
        val r = evaluate(
            weights = genWeights(startKg = 75.0, slopeKgPerWeek = -0.6, seed = 7),
            calories = genCalories(1700),
        )
        val slope = requireNotNull(r.math.slopeKgPerWeek)
        assertTrue("slope=$slope 应显著为负", slope < -0.3)
        val low = requireNotNull(r.math.slopeLowKgPerWeek)
        val high = requireNotNull(r.math.slopeHighKgPerWeek)
        assertTrue("真值 -0.6 应落入 CI [$low, $high]", -0.6 >= low && -0.6 <= high)
        assertEquals(WeeklyControlEngine.SlopeConfidence.OK, r.math.slopeConfidence)
    }

    // ------------------------------------------------------------- 五例 HOLD（Success Criteria）

    @Test
    fun `增重目标HOLD`() {
        val r = evaluate(
            weights = genWeights(70.0, 0.3),
            calories = genCalories(2500),
            targetWeightKg = 80.0, // 高于窗口首周均值+0.5 → 增重
        )
        assertEquals(WeeklyControlEngine.ControlDecision.Hold(WeeklyControlEngine.HoldKind.GAINING_UNSUPPORTED), r.decision)
    }

    @Test
    fun `摄入未超基准HOLD`() {
        // 自适应基准（9A）：TDEE=2000+350=2350 → 基准 1850，2000 ≤ 1850×1.15=2127 → 未超
        // 低噪声保证斜率估计稳定，断言不被回归噪声翻转
        val r = evaluate(genWeights(80.0, -0.35, noiseKg = 0.3), genCalories(2000))
        assertEquals(WeeklyControlEngine.ControlDecision.Hold(WeeklyControlEngine.HoldKind.WITHIN_BUDGET), r.decision)
    }

    @Test
    fun `斜率快于目标HOLD`() {
        // 规则优先级用例（非噪声鲁棒性用例）：低噪声保证斜率估计明确快于 -0.5
        val r = evaluate(genWeights(80.0, -0.6, seed = 3, noiseKg = 0.3), genCalories(2500))
        assertEquals(WeeklyControlEngine.ControlDecision.Hold(WeeklyControlEngine.HoldKind.ON_TRACK_SLOPE), r.decision)
    }

    @Test
    fun `无目标HOLD`() {
        val r = evaluate(genWeights(75.0, -0.2), genCalories(1800), targetWeightKg = 0.0)
        assertEquals(WeeklyControlEngine.ControlDecision.Hold(WeeklyControlEngine.HoldKind.NO_GOAL), r.decision)
    }

    @Test
    fun `越过目标落入维持区按无目标HOLD`() {
        // 窗口首周均值约 75，目标 75.2 → |Δ|≤0.5 → 维持区；低噪声防判定基准漂移出界
        val r = evaluate(genWeights(75.0, -0.2, noiseKg = 0.3), genCalories(1800), targetWeightKg = 75.2)
        assertEquals(WeeklyControlEngine.ControlDecision.Hold(WeeklyControlEngine.HoldKind.NO_GOAL), r.decision)
    }

    // ------------------------------------------------------------- 微调路径

    @Test
    fun `减重慢于目标且超基准输出减食物建议`() {
        // 自适应基准：TDEE≈2500+100=2600 → 基准≈2100，2500 > 2100×1.15=2415 → 微调
        val r = evaluate(
            weights = genWeights(80.0, -0.1, noiseKg = 0.3), // 慢于目标 -0.5
            calories = genCalories(2500),
            indulgent = listOf(
                WeeklyControlEngine.IndulgentMeal("深夜泡面", 4, 550),
                WeeklyControlEngine.IndulgentMeal("薯片", 2, 300),
            ),
        )
        val adj = r.decision as WeeklyControlEngine.ControlDecision.Adjust
        val cut = adj.suggestion as WeeklyControlEngine.AdjustSuggestion.CutFood
        assertEquals("深夜泡面", cut.meal.foodName)
        assertEquals(4, cut.meal.mealCount)
        assertTrue("超额 ${adj.excessKcalPerDay} 应在噪声容忍区间", adj.excessKcalPerDay in 300..500)
    }

    @Test
    fun `超预算但无INDULGENT走份量兜底`() {
        // TDEE≈2400+100=2500 → 基准≈2000，2400 > 2300 → 份量兜底
        val r = evaluate(genWeights(80.0, -0.1, noiseKg = 0.3), genCalories(2400), indulgent = emptyList())
        val adj = r.decision as WeeklyControlEngine.ControlDecision.Adjust
        val portion = adj.suggestion as WeeklyControlEngine.AdjustSuggestion.CutPortion
        assertTrue("超额 ${portion.excessKcalPerDay} 应在噪声容忍区间", portion.excessKcalPerDay in 300..500)
    }

    // ------------------------------------------------------------- 护栏与降级

    @Test
    fun `周覆盖低于50百分比被排除并记录原因`() {
        // 只给每周 3 天记录（3/7 < 50%）→ 全部排除 → 无计入周
        val sparse = buildList {
            var wk = windowStart.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val lastWk = today.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(1)
            while (!wk.isAfter(lastWk)) {
                listOf(0, 1, 2).forEach { add(DailyCalories((wk.plusDays(it.toLong())).toString(), 2000)) }
                wk = wk.plusWeeks(1)
            }
        }
        val r = evaluate(genWeights(80.0, -0.2), sparse)
        assertEquals(0, r.math.includedWeekCount)
        assertEquals(WeeklyControlEngine.TdeeState.NO_INCLUDED_WEEKS, r.math.tdeeState)
        assertTrue(r.math.weekStats.any { it.excludeReason == WeeklyControlEngine.ExcludeReason.LOW_COVERAGE })
    }

    @Test
    fun `周均摄入低于BMR下限被排除`() {
        // BMR(男30岁175cm80kg)≈1767 → 下限≈1414；给 1200/天全覆盖
        val r = evaluate(genWeights(80.0, -0.2), genCalories(1200))
        assertTrue(r.math.weekStats.any { it.excludeReason == WeeklyControlEngine.ExcludeReason.BELOW_BMR_FLOOR })
        assertEquals(0, r.math.includedWeekCount)
    }

    @Test
    fun `BMR不可得周保守排除`() {
        val noProfile = profile.copy(gender = null)
        val r = evaluate(genWeights(80.0, -0.2), genCalories(2000), profileArg = noProfile, staticRecommendedIntake = 1900)
        assertTrue(r.math.weekStats.all { it.excludeReason == WeeklyControlEngine.ExcludeReason.BMR_UNAVAILABLE })
        assertEquals(WeeklyControlEngine.TdeeState.NO_INCLUDED_WEEKS, r.math.tdeeState)
    }

    @Test
    fun `体重不足14天整体数据不足`() {
        val r = evaluate(
            weights = genWeights(80.0, -0.2, from = today.minusDays(12)), // 13 天 < 14
            calories = genCalories(2000),
        )
        val ins = r.decision as WeeklyControlEngine.ControlDecision.Insufficient
        assertEquals(WeeklyControlEngine.InsufficientKind.WEIGHT_DAYS, ins.kind)
    }

    @Test
    fun `饮食晚于体重仅一完整周TDEE积累中退静态基准`() {
        // 两侧同窗（P1 修复的行为锁定）：体重全窗口、饮食只记最近 2 周——
        // 体重斜率取样起点对齐到首个统计周 07-20（不因饮食晚开始而跨 6 周）
        val r = evaluate(
            weights = genWeights(80.0, -0.2),                       // 07-18 起全窗口
            calories = genCalories(2500, from = today.minusDays(12)), // 08-16 起仅 2 周
        )
        assertEquals(40, r.math.weightDays) // 对齐后仍满 40 天（丢弃残缺首周 2 天）
        assertEquals(WeeklyControlEngine.TdeeState.ACCUMULATING, r.math.tdeeState)
        assertEquals(null, r.math.adaptiveTdeeKcal)
        assertEquals(false, r.math.baselineFromAdaptive)
        assertEquals(1900, r.math.baselineKcal) // 静态兜底
        // 决策照常出（退静态基准）
        assertTrue(r.decision is WeeklyControlEngine.ControlDecision.Adjust)
    }

    @Test
    fun `短体重数据按对齐后起点计天数`() {
        // 体重从 08-16 起：对齐到 08-17 后仅 12 天 <14 → 整体数据不足（新语义）
        val r = evaluate(
            weights = genWeights(80.0, -0.2, from = today.minusDays(12)),
            calories = genCalories(2500, from = today.minusDays(12)),
        )
        val ins = r.decision as WeeklyControlEngine.ControlDecision.Insufficient
        assertEquals(WeeklyControlEngine.InsufficientKind.WEIGHT_DAYS, ins.kind)
    }

    @Test
    fun `残缺首周不参与统计`() {
        // 窗口起点 07-18 是周六：07-18/19 两天有记录也不应产生任何周统计影响
        val r = evaluate(genWeights(80.0, -0.2), genCalories(2000))
        val weeks = r.math.weekStats.map { it.weekStart }
        assertEquals(5, weeks.size) // 07-20 起共 5 个完整周
        assertEquals(LocalDate.of(2026, 7, 20), weeks.first())
    }

    @Test
    fun `当前不完整周不参与统计`() {
        val r = evaluate(genWeights(80.0, -0.2), genCalories(2000))
        assertTrue(r.math.weekStats.none { it.weekStart >= LocalDate.of(2026, 8, 24) })
    }

    @Test
    fun `弹性窗口标注实际天数`() {
        val r = evaluate(
            weights = genWeights(80.0, -0.2, from = today.minusDays(20)),
            calories = genCalories(2000, from = today.minusDays(20)),
        )
        assertEquals(21, r.math.windowDays)
    }

    // ------------------------------------------------------------- TDEE 对比与警报

    @Test
    fun `自适应与静态偏差超阈值触发只读警报`() {
        // TDEE 反推≈摄入-slope*7000=2500-(-0.1/7*7000)=2600；静态 BMR1767*1.55≈2739 → 偏差~5% 不触发
        // 改用更高摄入拉开差距：3300-100=3200 vs 2739 → 17% 触发
        val r = evaluate(genWeights(80.0, -0.1, seed = 5), genCalories(3300))
        assertTrue(r.math.tdeeDeviationAlert)
    }

    // ------------------------------------------------------------- INDULGENT 聚合（T7）

    private val json = Json { ignoreUnknownKeys = true }

    private fun foodJson(vararg items: String) = "[" + items.joinToString(",") + "]"

    @Test
    fun `聚合按餐次计数且同名去重`() {
        val noodle = """{"name":"泡面","estimatedCalories":550,"quality":"INDULGENT"}"""
        val chips = """{"name":"薯片","estimatedCalories":300,"quality":"INDULGENT"}"""
        val rice = """{"name":"米饭","estimatedCalories":200,"quality":"OFTEN"}"""
        val meals = listOf(
            foodJson(noodle, rice),        // 餐1：泡面
            foodJson(noodle, noodle, chips), // 餐2：同名去重 → 泡面1次+薯片1次
            foodJson(rice),                // 餐3：无
            "not-json",                    // 解析失败跳过
        )
        val result = WeeklyControlEngine.aggregateIndulgentMeals(meals, json)
        assertEquals(listOf("泡面" to 2, "薯片" to 1), result.map { it.foodName to it.mealCount })
        assertEquals(550, result.first().medianCalories)
    }

    @Test
    fun `聚合忽略非INDULGENT与空名`() {
        val ok = """{"name":"沙拉","estimatedCalories":150,"quality":"OFTEN"}"""
        val empty = """{"name":"  ","estimatedCalories":100,"quality":"INDULGENT"}"""
        assertTrue(WeeklyControlEngine.aggregateIndulgentMeals(listOf(foodJson(ok, empty)), json).isEmpty())
    }

    private companion object {
        const val DAY_MS = 86_400_000L
    }
}
