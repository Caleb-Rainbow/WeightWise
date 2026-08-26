package com.example.weight.data.chat

import com.example.weight.data.diet.DailyCalories
import com.example.weight.data.health.HealthActivitySummary
import com.example.weight.data.record.Record
import com.example.weight.util.TrendConfidence
import com.example.weight.util.WeightTrendInsight
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 体重 AI 分析的 Prompt 构建器。
 * 从 MainViewModel 抽出的纯函数，入参全部显式传入，便于单元测试。
 */
object AnalysisPromptBuilder {

    /** 逐日热量明细的天数上限；超出后按月聚合日均，避免近 3 年档位把 Prompt 撑爆 */
    private const val DAILY_DETAIL_LIMIT_DAYS = 62

    /** 逐条体重记录的条数上限；超出后（如年报）按月聚合，避免 Prompt 过长 */
    private const val RECORD_DETAIL_LIMIT = 62

    /**
     * @param records 数据库中按所选范围取出的记录列表（时间不限序）
     * @param scopeLabel 界面所选时间范围的展示名，须与取数范围一致，避免 AI 基于错误前提分析
     * @param dailyCalories 所选范围内每日摄入热量合计；空列表表示无饮食记录，不输出热量段
     * @param healthSummary Health Connect 的周期聚合数据；null 表示不可用，不输出活动与恢复段
     * @param age / genderLabel / activityLabel 个人档案，未设置时传 0 / 空串，对应行不写入
     */
    fun build(
        records: List<Record>,
        scopeLabel: String,
        bmi: Double,
        heightCm: Double,
        targetWeight: Double,
        dailyCalories: List<DailyCalories> = emptyList(),
        age: Int = 0,
        genderLabel: String = "",
        activityLabel: String = "",
        healthSummary: HealthActivitySummary? = null,
        trendInsight: WeightTrendInsight? = null,
    ): String {
        val recordsString = recordsText(records)

        val profileExtras = buildList {
            if (age > 0) add("- 年龄：${age}岁")
            if (genderLabel.isNotBlank()) add("- 性别：$genderLabel")
            if (activityLabel.isNotBlank()) add("- 活动水平：$activityLabel")
        }.let { if (it.isEmpty()) "" else it.joinToString("\n", postfix = "\n") }

        val caloriesSection = if (dailyCalories.isEmpty()) "" else run {
            val header = "【热量摄入】（来自饮食记录的估算值）\n"
            val body = dailyCaloriesText(dailyCalories)
            "$header$body\n\n"
        }
        // 洞察要求中的饮食关联句同样只在提供了热量数据时出现
        val caloriesInsight = if (dailyCalories.isEmpty()) "" else
            "若上方提供了【热量摄入】数据，请结合热量摄入与体重变化的关系进行分析" +
                "（例如摄入持续偏高对应体重上升），帮助用户理解体重波动背后的饮食原因。"
        val healthSection = healthSummary?.let { summary ->
            val avgSteps = summary.steps / summary.rangeDays.coerceAtLeast(1)
            val avgBurned = summary.totalCaloriesBurned / summary.rangeDays.coerceAtLeast(1)
            val avgSleepMinutes = summary.sleepMinutes / summary.rangeDays.coerceAtLeast(1)
            """【活动与恢复】（来自 Health Connect 的周期聚合）
- 日均步数：$avgSteps 步
- 日均总消耗：$avgBurned kcal（包含基础代谢与活动消耗）
- 日均睡眠：${avgSleepMinutes / 60}小时${avgSleepMinutes % 60}分钟

"""
        }.orEmpty()
        val healthInsight = if (healthSummary == null) "" else
            "若上方提供了【活动与恢复】数据，请结合步数、总消耗和睡眠与体重趋势进行相关性分析，但不要把相关性描述为确定的因果关系。"
        val trendSection = trendInsight?.let { insight ->
            val rate = insight.weeklyRateKg?.let {
                "${String.format(Locale.CHINA, "%+.2f", it)}kg/周"
            } ?: "暂不可计算"
            """【趋势信号】（由应用按自然日间隔计算）
- 数据可信度：${insight.confidence.label}；${insight.confidenceReason}
- 平滑趋势方向：${insight.direction.label}；近期速率：$rate
- 平台期信号：${if (insight.isPlateau) "有" else "无"}

"""
        }.orEmpty()
        val lowConfidenceLimit = if (trendInsight?.confidence == TrendConfidence.LOW) {
            "- 当前趋势可信度较低，只能描述已记录的事实并提醒继续记录；不得断言已进入平台期，也不得把短期变化归因于饮食、运动或疾病。\n"
        } else ""

        return """
你是一位专业、温暖且富有同理心的体重管理顾问。请根据用户的体重记录和日志，为TA提供一份简单易懂、具有鼓励性的分析反馈。

【用户档案】
- 身高：${heightCm}cm
- 目标体重：${targetWeight}kg
- 当前BMI：$bmi
- 数据时间范围：$scopeLabel
$profileExtras【打卡数据】
$recordsString

$caloriesSection$healthSection$trendSection【回复要求】
请以亲切的朋友口吻直接与用户对话（称呼“你”），字数控制在300字左右，并严格按以下三个段落结构输出：

1. 阶段总结：用一两句话概括用户在这段时间（$scopeLabel）的体重变化趋势（如：稳步下降、遇到平台期、轻微波动等），并给予情绪上的肯定或安抚。
2. 数据洞察：结合体重数值的变化和用户的[日志]内容，分析可能的原因。如果日志提到了饮食/运动/情绪，请指出它们与体重变化的关联；如果没有日志，请基于纯数值趋势进行合理推断。$caloriesInsight$healthInsight
3. 行动建议：基于现状，给出1到2个具体、微小且容易执行的日常建议，帮助用户向 ${targetWeight}kg 的目标迈进。

【严格限制条件】
- 必须使用纯文本，绝对不要输出任何代码块、Markdown复杂表格或特殊图标。
- 语言必须通俗易懂，绝对不要使用生僻的医学术语。
- 不要机械地罗列或复述用户的数据，你的重点是“解读数据背后的意义”。
$lowConfidenceLimit
""".trimIndent()
    }

    /** 逐条明细；记录超过 [RECORD_DETAIL_LIMIT] 条改按月聚合（打卡天数/日均/最高/最低），控制 Prompt 长度 */
    private fun recordsText(records: List<Record>): String {
        val zone = ZoneId.systemDefault() // 每条记录重复获取有查找成本，取一次复用
        if (records.size <= RECORD_DETAIL_LIMIT) {
            return records.joinToString("\n") { record ->
                val date = Instant.ofEpochMilli(record.timestamp)
                    .atZone(zone)
                    .toLocalDate()
                    .format(DateTimeFormatter.ISO_LOCAL_DATE)
                val logText = if (record.log.isNotBlank()) " [日志: ${record.log}]" else ""
                "- $date: ${String.format(Locale.CHINA, "%.1f", record.weight)}kg$logText"
            }
        }
        return records
            .groupBy { record ->
                Instant.ofEpochMilli(record.timestamp)
                    .atZone(zone)
                    .toLocalDate()
                    .toString()
                    .take(7)
            }
            .map { (month, monthRecords) ->
                val days = monthRecords
                    .map { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() }
                    .distinct()
                    .size
                val weights = monthRecords.map { it.weight }
                "- $month: 打卡 $days 天，平均 ${String.format(Locale.CHINA, "%.1f", weights.average())}kg，" +
                    "最高 ${String.format(Locale.CHINA, "%.1f", weights.max())}kg，最低 ${String.format(Locale.CHINA, "%.1f", weights.min())}kg"
            }
            .joinToString("\n")
    }

    /** 逐日明细；范围超过 [DAILY_DETAIL_LIMIT_DAYS] 天改按月聚合日均，控制 Prompt 长度 */
    private fun dailyCaloriesText(dailyCalories: List<DailyCalories>): String {
        if (dailyCalories.size <= DAILY_DETAIL_LIMIT_DAYS) {
            return dailyCalories.joinToString("\n") { "- ${it.date}: ${it.calories} kcal" }
        }
        return dailyCalories
            .groupBy { it.date.take(7) }
            .map { (month, days) ->
                val avg = days.sumOf { it.calories }.toDouble() / days.size
                "- $month: 日均 ${avg.roundToInt()} kcal"
            }
            .joinToString("\n")
    }
}
