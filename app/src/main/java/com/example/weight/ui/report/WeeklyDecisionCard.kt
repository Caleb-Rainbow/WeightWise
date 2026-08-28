package com.example.weight.ui.report

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.weight.util.WeeklyControlEngine
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.abs

/**
 * 每周决策卡（设计定稿 Variant C 折角行动条，2026-08-28 /plan-design-review）：
 * - 28/8dp 对角折角 + 纯英文眉题 WEEKLY DECISION，紧凑条状
 * - 三态语义：保持=墨色动词 / 微调=朱砂动词+超 N% / 数据不足=降不透明度+缺口明细；
 *   红黄绿保留给饮食语义不占用
 * - 指令式文案；算式默认收起，展开四块依据（可解释性硬要求，被排除周逐条列明）
 * - [result] 为 null 时固定高度占位防布局跳动（评审 2A）
 * - TalkBack 三段连读「本周决策+动词+副文案」；展开控件带 Role 与状态（评审 5A）
 */
@Composable
fun WeeklyDecisionCard(
    result: WeeklyControlEngine.WeeklyControlResult?,
    targetWeightKg: Double,
    modifier: Modifier = Modifier,
) {
    if (result == null) {
        // 查询空窗占位：与展开前卡片等高，避免报告页滚动跳动
        Spacer(modifier = modifier.fillMaxWidth().height(118.dp))
        return
    }
    val decision = result.decision
    val math = result.math
    var expanded by remember { mutableStateOf(false) }

    val isAdjust = decision is WeeklyControlEngine.ControlDecision.Adjust
    val isInsufficient = decision is WeeklyControlEngine.ControlDecision.Insufficient
    val (verb, subline) = cardTexts(decision, math, targetWeightKg)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = "本周决策，$verb，$subline"
            },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 8.dp, bottomEnd = 12.dp, bottomStart = 12.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 12.dp)) {
            // 眉题行：WEEKLY DECISION + 窗口天数标注 + 低置信标记（2A）
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "WEEKLY DECISION",
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 2.5.sp,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.weight(1f))
                if (math.slopeConfidence == WeeklyControlEngine.SlopeConfidence.LOW &&
                    math.slopeKgPerWeek != null
                ) {
                    Text(
                        text = "低置信",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
                Text(
                    text = "基于 ${math.windowDays} 天",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = verb,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        isAdjust -> MaterialTheme.colorScheme.tertiary // 朱砂=需注意
                        isInsufficient -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.weight(1f),
                )
                if (isAdjust) {
                    val overPct = math.baselineKcal?.takeIf { it > 0 }?.let { base ->
                        ((math.avgIntakeKcal?.toDouble() ?: 0.0) / base * 100 - 100).toInt()
                    }
                    if (overPct != null && overPct > 0) {
                        Text(
                            text = "超 $overPct%",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }
            }
            Text(
                text = subline,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
            )
                // 展开控件（数据不足态无依据可展开）
                if (!isInsufficient) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .heightIn(min = 48.dp) // 评审 5A：可点击目标 ≥48dp
                            .clickable(role = Role.Button) { expanded = !expanded }
                            .semantics { stateDescription = if (expanded) "已展开" else "已收起" },
                    ) {
                    Text(
                        text = if (expanded) "收起依据 ▴" else "查看依据 ▾",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                AnimatedVisibility(visible = expanded) {
                    Column {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                        DecisionMathLines(math)
                    }
                }
            }
        }
    }
}

/** 指令式文案三段（动词 / 副行）；数据不足态副行带缺口明细 */
private fun cardTexts(
    decision: WeeklyControlEngine.ControlDecision,
    math: WeeklyControlEngine.ControlMath,
    targetWeightKg: Double,
): Pair<String, String> = when (val d = decision) {
    is WeeklyControlEngine.ControlDecision.Hold -> when (d.kind) {
        WeeklyControlEngine.HoldKind.ON_TRACK_SLOPE ->
            "继续保持" to "下降 ${String.format(Locale.CHINA, "%.2f", abs(math.slopeKgPerWeek ?: 0.0))} kg/周，达到目标节奏"
        WeeklyControlEngine.HoldKind.WITHIN_BUDGET ->
            "继续保持" to "周均摄入在基准 ${math.baselineKcal ?: "—"} kcal 的 115% 以内"
        WeeklyControlEngine.HoldKind.NO_GOAL ->
            // goalBase 判定基准不足（窗口起点 7 天内 <3 个体重日）与"未设/已达标"分开，
            // 否则设了目标但称重晚开始的用户会看到错误的"维持区"文案
            if (math.goalBaseWeightKg == null) "暂无每周决策" to "体重记录不足，先积累称重数据"
            else if (targetWeightKg > 0) "保持现状" to "已进入目标 ±0.5kg 维持区"
            else "暂无每周决策" to "设置目标体重后开启"
        WeeklyControlEngine.HoldKind.GAINING_UNSUPPORTED ->
            "增重模式" to "每周决策暂只支持减重目标"
    }
    is WeeklyControlEngine.ControlDecision.Insufficient -> "本周先补记录" to d.detail
    is WeeklyControlEngine.ControlDecision.Adjust -> when (val s = d.suggestion) {
        is WeeklyControlEngine.AdjustSuggestion.CutFood ->
            "减掉：${s.meal.foodName}" to "近 60 天 ${s.meal.mealCount} 餐 · 放纵频次最高"
        is WeeklyControlEngine.AdjustSuggestion.CutPortion ->
            "减少整体份量" to "每天约少 ${s.excessKcalPerDay} kcal（60 天内无放纵食物）"
    }
}

/** 算式四块：摄入/斜率/护栏/TDEE（评审 2A：被排除周逐条列明，不静默隐藏） */
@Composable
private fun DecisionMathLines(math: WeeklyControlEngine.ControlMath) {
    val weekFmt = DateTimeFormatter.ofPattern("M/d")
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        MathLine(
            "周均摄入",
            math.avgIntakeKcal?.let { "$it kcal（按已记录日均值）" } ?: "无达标周数据",
        )
        MathLine(
            "决策基准",
            math.baselineKcal?.let {
                "$it kcal（" + if (math.baselineFromAdaptive) "自适应 TDEE−目标缺口" else "静态公式" + "）"
            } ?: "—",
        )
        val slope = math.slopeKgPerWeek
        MathLine(
            "体重斜率",
            if (slope != null) {
                val ci = if (math.slopeLowKgPerWeek != null && math.slopeHighKgPerWeek != null) {
                    "，区间 " + String.format(
                        Locale.CHINA, "%.2f~%.2f", math.slopeLowKgPerWeek, math.slopeHighKgPerWeek,
                    )
                } else ""
                "${String.format(Locale.CHINA, "%.2f", slope)} kg/周$ci · 有效 ${math.weightDays} 天"
            } else "不可估计",
        )
        val tdeeText = when (math.tdeeState) {
            WeeklyControlEngine.TdeeState.READY -> {
                val base = "自适应 ${math.adaptiveTdeeKcal} kcal"
                val compare = math.staticTdeeKcal?.let {
                    val flag = if (math.tdeeDeviationAlert) " ⚠偏差>15%" else ""
                    " vs 静态 $it$flag"
                }.orEmpty()
                base + compare
            }
            WeeklyControlEngine.TdeeState.ACCUMULATING ->
                "数据积累中（${math.includedWeekCount}/${WeeklyControlEngine.MIN_INCLUDED_WEEKS} 个达标周）"
            WeeklyControlEngine.TdeeState.NO_INCLUDED_WEEKS -> "尚无达标饮食周"
        }
        MathLine("能量反推", tdeeText)
        val guard = buildString {
            append("计入 ${math.includedWeekCount}/${math.weekStats.size} 个完整周")
            math.weekStats.filter { !it.included }.forEach { w ->
                val label = w.weekStart.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
                    .format(weekFmt)
                val reason = when (w.excludeReason) {
                    WeeklyControlEngine.ExcludeReason.LOW_COVERAGE -> "覆盖不足"
                    WeeklyControlEngine.ExcludeReason.BELOW_BMR_FLOOR -> "摄入偏低"
                    WeeklyControlEngine.ExcludeReason.BMR_UNAVAILABLE -> "档案不全"
                    else -> "未计入"
                }
                append("\n$label 周 · $reason（记录 ${w.loggedDays}/7 天")
                w.avgKcal?.let { append("，日均 $it kcal") }
                append("）")
            }
        }
        MathLine("护栏", guard)
        if (math.tdeeDeviationAlert) {
            Text(
                text = "自适应与静态公式偏差超过 15%，仅供参考，不改动现有建议摄入",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            )
        }
    }
}

@Composable
private fun MathLine(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            letterSpacing = 1.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
        )
    }
}
