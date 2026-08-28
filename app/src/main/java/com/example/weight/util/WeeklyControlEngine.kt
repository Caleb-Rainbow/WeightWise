package com.example.weight.util

import com.example.weight.data.diet.DailyCalories
import com.example.weight.data.diet.FoodQuality
import com.example.weight.data.diet.RecognizedFoodItem
import com.example.weight.data.record.DailyWeight
import kotlinx.serialization.json.Json
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * 每周控制回路引擎（纯函数）：观察（体重斜率+置信区间）→ 判断（实际斜率 vs 目标斜率 +
 * 能量守恒反推自适应 TDEE）→ 建议（保持/微调一件事/数据不足）。
 *
 * 设计定稿（docs/designs/weekly-control-loop-v1.md，2026-08-28 评审链）：
 * - 斜率来自窗口内**原始**日代表值序列的 OLS 回归（EWMA 仅作展示，不参与推断：
 *   因果递推的强自相关会系统性缩窄残差 CI）；禁止首尾差法（±0.8kg 日噪声下
 *   4 周窗口反推误差 ≈283 kcal/天，与 15% 提示阈值同量级）
 * - 统计只用**已完结自然周**（周一界，北京日字符串直算）；当前不完整周与
 *   周中开始的残缺首周不参与；体重斜率窗口与摄入统计窗口同一起点
 * - 周均摄入口径 = 已记录日的均值（calories>0 的天才计），未记日不按 0 不外推
 * - 双护栏：周覆盖 ≥50% 且 周已记录日均 ≥ BMR×0.8（BMR 不可得时保守排除）
 * - TDEE = 计入周已记录日摄入均值 − slopePerDay × KCAL_PER_KG（与 CalorieCalculator 同源）
 * - 微调三必要条件（缺一即 HOLD）：减重目标 ∧ 实际斜率慢于目标 ∧ 摄入超基准 >15%
 * - 基准 = 自适应 TDEE − 目标缺口（TDEE 可用时）；静态 recommendedIntake 兜底
 * - 自适应值只在决策计算内部使用，不接入 RecommendedIntakeProvider
 */
object WeeklyControlEngine {

    /** 反推窗口默认 6 周；实际窗口 = min(此值, 可用跨度)（弹性，卡片标注实际天数） */
    const val WINDOW_DAYS = 42

    /** 有效体重日下限：低于此值整体"数据不足"，不输出猜测 */
    const val MIN_WEIGHT_DAYS = 14

    /** TDEE 输出最少计入完整周数：1 周噪声过大，显示"数据积累中 n/2" */
    const val MIN_INCLUDED_WEEKS = 2

    /** 周覆盖护栏：已记录天数 / 7 */
    const val WEEK_COVERAGE_THRESHOLD = 0.5

    /** 周摄入下限护栏：周已记录日均 ≥ BMR×此比值（防"每天只记一餐"式系统性低估） */
    const val INTAKE_FLOOR_BMR_RATIO = 0.8

    /** 微调触发的超基准幅度 */
    const val OVER_BUDGET_THRESHOLD = 1.15

    /** 目标方向判定缓冲（与 CalorieCalculator.recommendedIntake 的 ±0.5 一致） */
    const val TARGET_BUFFER_KG = 0.5

    /** 自适应 TDEE 与静态公式偏差超过此比值时只读提示 */
    const val TDEE_DEVIATION_ALERT = 0.15

    /** 斜率置信带宽度（kg/周）超过此值时标记低置信、不画带 */
    const val SLOPE_BAND_MAX_KG_PER_WEEK = 0.4

    /** 目标判定基准：窗口起点起 7 天内体重均值；样本不足此值不判目标 */
    private const val GOAL_BASE_MIN_DAYS = 3

    // ------------------------------------------------------------------ 输入

    data class ControlProfile(
        val gender: Gender?,
        val age: Int,
        val heightCm: Double,
        val activityLevel: ActivityLevel?,
    )

    /** 60 天窗口内 INDULGENT 食物统计（[aggregateIndulgentMeals] 输出） */
    data class IndulgentMeal(
        val foodName: String,
        val mealCount: Int,
        val medianCalories: Int,
    )

    data class WeeklyControlInput(
        val today: LocalDate,
        val dailyWeights: List<DailyWeight>,
        val dailyCalories: List<DailyCalories>,
        val targetWeightKg: Double,
        val weeklyTargetChangeKg: Double,
        val profile: ControlProfile?,
        /** 静态建议摄入（档案不全时为 null；TDEE 不可用时的决策基准兜底） */
        val staticRecommendedIntake: Int?,
        val indulgentMeals: List<IndulgentMeal>,
        val windowDays: Int = WINDOW_DAYS,
    )

    // ------------------------------------------------------------------ 输出

    enum class HoldKind { ON_TRACK_SLOPE, WITHIN_BUDGET, NO_GOAL, GAINING_UNSUPPORTED }

    enum class InsufficientKind { WEIGHT_DAYS, NO_BASELINE, NO_DIET_WEEKS, SLOPE_SPAN }

    enum class TdeeState {
        /** 计入周足够，TDEE 已反推 */
        READY,

        /** 计入周 < [MIN_INCLUDED_WEEKS]，显示"数据积累中 n/N"；决策退静态基准 */
        ACCUMULATING,

        /** 无任何计入周（全部被护栏挡掉）或 BMR 不可得 */
        NO_INCLUDED_WEEKS,
    }

    /** 斜率置信度：LOW 时不画带、卡片标"低置信" */
    enum class SlopeConfidence { OK, LOW }

    enum class ExcludeReason { LOW_COVERAGE, BELOW_BMR_FLOOR, BMR_UNAVAILABLE }

    data class WeekStat(
        val weekStart: LocalDate,
        /** 周内已记录日（calories>0）天数 */
        val loggedDays: Int,
        /** 已记录日均值；无记录日为 null */
        val avgKcal: Int?,
        val included: Boolean,
        val excludeReason: ExcludeReason?,
    )

    /** 微调建议：减掉某食物 或 份量兜底（窗口无 INDULGENT 时） */
    sealed interface AdjustSuggestion {
        data class CutFood(val meal: IndulgentMeal) : AdjustSuggestion
        data class CutPortion(val excessKcalPerDay: Int) : AdjustSuggestion
    }

    sealed interface ControlDecision {
        data class Hold(val kind: HoldKind) : ControlDecision
        data class Adjust(val suggestion: AdjustSuggestion, val excessKcalPerDay: Int) : ControlDecision
        data class Insufficient(val kind: InsufficientKind, val detail: String) : ControlDecision
    }

    /** 决策卡展开依据的全部算术（UI 逐行展示，可解释性是硬要求） */
    data class ControlMath(
        val windowDays: Int,
        val weightDays: Int,
        val slopeKgPerWeek: Double?,
        val slopeLowKgPerWeek: Double?,
        val slopeHighKgPerWeek: Double?,
        val slopeConfidence: SlopeConfidence,
        val weekStats: List<WeekStat>,
        val includedWeekCount: Int,
        /** 计入周全部已记录日的均值 */
        val avgIntakeKcal: Int?,
        val adaptiveTdeeKcal: Int?,
        val tdeeState: TdeeState,
        /** 静态公式 TDEE（BMR×活动系数），档案不全为 null */
        val staticTdeeKcal: Int?,
        /** 自适应与静态偏差超阈值时提示（只读，不替换） */
        val tdeeDeviationAlert: Boolean,
        /** 本次决策基准与其来源 */
        val baselineKcal: Int?,
        val baselineFromAdaptive: Boolean,
        /** 目标方向判定基准：窗口起点起 7 天体重均值 */
        val goalBaseWeightKg: Double?,
        /** 目标缺口（kcal/日）：weeklyTargetChangeKg×7000/7，与 CalorieCalculator 同式同源常量 */
        val dailyDeficitKcal: Int,
    )

    data class WeeklyControlResult(
        val decision: ControlDecision,
        val math: ControlMath,
    )

    // ------------------------------------------------------------------ 入口

    fun evaluate(input: WeeklyControlInput): WeeklyControlResult {
        // 弹性窗口（5A）：统计窗口 = max(配置窗口起点, 首个数据日)
        val configuredStart = input.today.minusDays((input.windowDays - 1).toLong())
        val firstDataDay = listOfNotNull(
            input.dailyWeights.minOfOrNull { it.recordDay },
            input.dailyCalories.minOfOrNull { it.date },
        ).minOrNull()?.let { parseDayOrNull(it) } ?: configuredStart
        val windowStart = maxOf(configuredStart, firstDataDay)

        // 能量守恒两侧同一起点（P1 修复）：摄入统计从首个完整周（丢弃残缺首周）开始，
        // 体重斜率与目标基准的取样起点与之对齐，否则"饮食早于体重"场景下两侧跨度
        // 错位会让 TDEE 反推的守恒假设静默失效
        val statStart = if (windowStart.dayOfWeek == DayOfWeek.MONDAY) windowStart
        else windowStart.with(TemporalAdjusters.next(DayOfWeek.MONDAY))

        // ---- 体重侧：原始序列回归（起点与首个计入周对齐） ----
        val weights = input.dailyWeights
            .filter { it.recordDay >= statStart.toString() }
            .sortedBy { it.timestamp }
        val weightDays = weights.size
        val samples = weights.map { it.timestamp to it.value }
        val slopePerDay = WeightTrendAnalyzer.linearSlopePerDay(samples)
        val slopeWeek = slopePerDay?.times(7)
        val ci = slopePerDay?.let { slopeConfidenceInterval(samples, it) }

        // ---- 饮食侧：完整周 + 双护栏 ----
        val bmr = input.profile?.let { p ->
            CalorieCalculator.bmr(p.gender, weights.lastOrNull()?.value ?: 0.0, p.heightCm, p.age)
        }
        val dailyByDate = input.dailyCalories
            .filter { parseDayOrNull(it.date) != null }
            .groupBy { parseDayOrNull(it.date)!!.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }
        val weekStats = buildWeekStats(input.today, windowStart, dailyByDate, bmr)
        val included = weekStats.filter { it.included }
        val avgIntake = included
            .flatMap { week -> weekStatsWeekDays(week, dailyByDate) }
            .takeIf { it.isNotEmpty() }
            ?.map { it.calories }
            ?.average()?.roundToInt()
        // ↑ 计入口径=计入周内全部已记录日（天数加权），与展示的"已记录日均值"一致

        // ---- TDEE 反推 ----
        val staticTdee = input.profile?.let { p ->
            bmr?.let { it * (p.activityLevel?.factor ?: ActivityLevel.SEDENTARY.factor) }?.roundToInt()
        }
        val (adaptiveTdee, tdeeState) = when {
            included.isEmpty() -> null to TdeeState.NO_INCLUDED_WEEKS
            included.size < MIN_INCLUDED_WEEKS -> null to TdeeState.ACCUMULATING
            slopePerDay != null && avgIntake != null ->
                (avgIntake - slopePerDay * CalorieCalculator.KCAL_PER_KG).roundToInt() to TdeeState.READY
            else -> null to TdeeState.NO_INCLUDED_WEEKS
        }
        val tdeeDeviationAlert = adaptiveTdee != null && staticTdee != null && staticTdee > 0 &&
            abs(adaptiveTdee - staticTdee).toDouble() / staticTdee > TDEE_DEVIATION_ALERT

        // ---- 基准与目标 ----
        val dailyDeficit = (input.weeklyTargetChangeKg.coerceIn(0.1, 1.0) *
            CalorieCalculator.KCAL_PER_KG / 7.0).roundToInt()
        val baseline: Int?
        val baselineFromAdaptive: Boolean
        if (adaptiveTdee != null) {
            // 安全下限与静态口径同源（CalorieCalculator.minIntake），防 TDEE 低估+激进目标
            // 产出劝减餐的过低基准
            baseline = maxOf(adaptiveTdee - dailyDeficit, CalorieCalculator.minIntake(input.profile?.gender))
            baselineFromAdaptive = true
        } else {
            baseline = input.staticRecommendedIntake
            baselineFromAdaptive = false
        }

        val goalBase = weights
            .mapNotNull { w -> parseDayOrNull(w.recordDay)?.let { d -> d to w.value } }
            .filter { it.first < statStart.plusDays(7) }
            .map { it.second }
            .takeIf { it.size >= GOAL_BASE_MIN_DAYS }
            ?.average()
        val goal = when {
            goalBase == null || input.targetWeightKg <= 0 -> GoalKind.NONE
            input.targetWeightKg < goalBase - TARGET_BUFFER_KG -> GoalKind.LOSING
            input.targetWeightKg > goalBase + TARGET_BUFFER_KG -> GoalKind.GAINING
            else -> GoalKind.NONE // 落入 ±0.5 维持区按无目标处理
        }

        // 弹性窗口：实际窗口天数（卡片标注）
        val effectiveWindow = (input.today.toEpochDay() - windowStart.toEpochDay() + 1).toInt()

        val math = ControlMath(
            windowDays = effectiveWindow,
            weightDays = weightDays,
            slopeKgPerWeek = slopeWeek,
            slopeLowKgPerWeek = ci?.first?.times(7),
            slopeHighKgPerWeek = ci?.second?.times(7),
            slopeConfidence = when {
                slopeWeek == null -> SlopeConfidence.LOW
                ci == null -> SlopeConfidence.LOW
                (ci.second - ci.first) * 7 > SLOPE_BAND_MAX_KG_PER_WEEK -> SlopeConfidence.LOW
                else -> SlopeConfidence.OK
            },
            weekStats = weekStats,
            includedWeekCount = included.size,
            avgIntakeKcal = avgIntake,
            adaptiveTdeeKcal = adaptiveTdee,
            tdeeState = tdeeState,
            staticTdeeKcal = staticTdee,
            tdeeDeviationAlert = tdeeDeviationAlert,
            baselineKcal = baseline,
            baselineFromAdaptive = baselineFromAdaptive,
            goalBaseWeightKg = goalBase,
            dailyDeficitKcal = dailyDeficit,
        )

        return WeeklyControlResult(decision = decide(input, goal, slopeWeek, baseline, avgIntake, math), math = math)
    }

    private enum class GoalKind { LOSING, GAINING, NONE }

    // 规则优先级（评审定稿：慢于目标是微调的必要条件，防"达目标但公式低估"时误劝减餐）
    private fun decide(
        input: WeeklyControlInput,
        goal: GoalKind,
        slopeWeek: Double?,
        baseline: Int?,
        avgIntake: Int?,
        math: ControlMath,
    ): ControlDecision = when {
        math.weightDays < MIN_WEIGHT_DAYS ->
            ControlDecision.Insufficient(
                InsufficientKind.WEIGHT_DAYS,
                "还需 ${MIN_WEIGHT_DAYS - math.weightDays} 个体重日",
            )
        slopeWeek == null ->
            ControlDecision.Insufficient(InsufficientKind.SLOPE_SPAN, "体重跨度不足，无法估计斜率")
        baseline == null || baseline <= 0 ->
            ControlDecision.Insufficient(InsufficientKind.NO_BASELINE, "档案不全，补全身高/年龄/性别后可用")
        goal == GoalKind.NONE ->
            ControlDecision.Hold(HoldKind.NO_GOAL)
        goal == GoalKind.GAINING ->
            ControlDecision.Hold(HoldKind.GAINING_UNSUPPORTED)
        // 减重目标：实际下降快于（含等于）目标速率 → 保持
        slopeWeek <= -input.weeklyTargetChangeKg ->
            ControlDecision.Hold(HoldKind.ON_TRACK_SLOPE)
        avgIntake == null ->
            ControlDecision.Insufficient(InsufficientKind.NO_DIET_WEEKS, "尚无达标饮食周，无法对比摄入")
        avgIntake <= (baseline * OVER_BUDGET_THRESHOLD).toInt() ->
            ControlDecision.Hold(HoldKind.WITHIN_BUDGET)
        else -> {
            val excess = avgIntake - baseline
            val cut = input.indulgentMeals.maxByOrNull { it.mealCount }
            ControlDecision.Adjust(
                suggestion = if (cut != null) AdjustSuggestion.CutFood(cut) else AdjustSuggestion.CutPortion(excess),
                excessKcalPerDay = excess,
            )
        }
    }

    // -------------------------------------------------------------- 周统计

    private fun buildWeekStats(
        today: LocalDate,
        windowStart: LocalDate,
        dailyByDate: Map<LocalDate, List<DailyCalories>>,
        bmr: Double?,
    ): List<WeekStat> {
        // 完整周：该周周日 < today（当前不完整周不参与）
        val lastCompleteWeekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(1)
        val firstWeekStart = windowStart.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val result = mutableListOf<WeekStat>()
        var wk = firstWeekStart
        while (!wk.isAfter(lastCompleteWeekStart)) {
            val weekEnd = wk.plusDays(6)
            // 残缺首周（窗口起点不在周一）直接丢弃：能量守恒两侧同一起点的周界基础
            if (wk == firstWeekStart && windowStart.dayOfWeek != DayOfWeek.MONDAY) {
                wk = wk.plusWeeks(1)
                continue
            }
            val logged = dailyByDate[wk].orEmpty().filter { it.calories > 0 && it.date <= weekEnd.toString() }
            val loggedDays = logged.map { it.date }.distinct().size
            val avg = logged.takeIf { it.isNotEmpty() }?.map { it.calories }?.average()?.roundToInt()
            val (included, reason) = when {
                loggedDays < 7 * WEEK_COVERAGE_THRESHOLD -> false to ExcludeReason.LOW_COVERAGE
                avg == null -> false to ExcludeReason.LOW_COVERAGE
                bmr == null -> false to ExcludeReason.BMR_UNAVAILABLE
                avg < bmr * INTAKE_FLOOR_BMR_RATIO -> false to ExcludeReason.BELOW_BMR_FLOOR
                else -> true to null
            }
            result += WeekStat(wk, loggedDays, avg, included, reason)
            wk = wk.plusWeeks(1)
        }
        return result
    }

    private fun weekStatsWeekDays(
        week: WeekStat,
        dailyByDate: Map<LocalDate, List<DailyCalories>>,
    ): List<DailyCalories> {
        val weekEnd = week.weekStart.plusDays(6)
        return dailyByDate[week.weekStart].orEmpty()
            .filter { it.calories > 0 && it.date <= weekEnd.toString() }
    }

    // -------------------------------------------------------------- 统计辅助

    /** OLS 斜率的 95% 置信区间（正态近似 1.96，原始序列，样本独立性假设按日粒度可接受） */
    private fun slopeConfidenceInterval(samples: List<Pair<Long, Double>>, slope: Double): Pair<Double, Double>? {
        val n = samples.size
        if (n < 3) return null
        val dayMs = 24.0 * 60 * 60 * 1000
        val firstTime = samples.first().first
        val xs = samples.map { (it.first - firstTime) / dayMs }
        val ys = samples.map { it.second }
        val meanX = xs.average()
        val meanY = ys.average()
        var sxx = 0.0
        var sse = 0.0
        for (i in samples.indices) {
            val dx = xs[i] - meanX
            sxx += dx * dx
            val fit = meanY + slope * dx
            sse += (ys[i] - fit) * (ys[i] - fit)
        }
        if (sxx <= 0.0) return null
        val mse = sse / (n - 2)
        val se = sqrt(mse / sxx)
        return (slope - 1.96 * se) to (slope + 1.96 * se)
    }

    // -------------------------------------------------------------- INDULGENT 统计（T7）

    /**
     * 60 天窗口内 INDULGENT 食物餐次统计：每餐内同名去重后按"该餐该食物项
     * quality==INDULGENT"计一次餐（不经 FrequentFoodAggregator——它先截总体
     * top-10 再过滤会漏掉第 11 名放纵食物）。调用方喂 [com.example.weight.data.diet.DietRecordDao.getFoodJsonBetween]。
     */
    fun aggregateIndulgentMeals(foodJsonList: List<String>, json: Json): List<IndulgentMeal> {
        data class Entry(val calories: MutableList<Int> = mutableListOf())

        val groups = LinkedHashMap<String, Entry>()
        for (foodJson in foodJsonList) {
            val foods = runCatching { json.decodeFromString<List<RecognizedFoodItem>>(foodJson) }
                .getOrNull() ?: continue
            for (food in foods.distinctBy { it.name.trim() }) {
                val key = food.name.trim()
                if (key.isEmpty() || food.effectiveQuality != FoodQuality.INDULGENT) continue
                groups.getOrPut(key) { Entry() }.calories.add(food.estimatedCalories)
            }
        }
        return groups.entries
            .map { (name, e) ->
                IndulgentMeal(
                    foodName = name,
                    mealCount = e.calories.size,
                    medianCalories = medianOfPositive(e.calories) ?: 0,
                )
            }
            .sortedWith(compareByDescending<IndulgentMeal> { it.mealCount }.thenBy { it.foodName })
    }

    /** 坏日期（手工改库/导入备份）返回 null 而非抛异常——与 aggregateIndulgentMeals 的 runCatching 防御风格一致 */
    private fun parseDayOrNull(day: String): LocalDate? = runCatching { LocalDate.parse(day) }.getOrNull()

    private fun medianOf(values: List<Int>): Int? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
    }

    private fun medianOfPositive(values: List<Int>): Int? = medianOf(values.filter { it > 0 })
}
