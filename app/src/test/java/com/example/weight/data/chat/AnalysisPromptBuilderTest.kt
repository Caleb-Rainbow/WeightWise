package com.example.weight.data.chat

import com.example.weight.data.diet.DailyCalories
import com.example.weight.data.health.HealthActivitySummary
import com.example.weight.data.record.Record
import com.example.weight.data.record.DailyWeight
import com.example.weight.util.WeightTrendAnalyzer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisPromptBuilderTest {

    /** 固定时间戳；期望日期用同一时区换算，测试不依赖运行环境的时区 */
    private val ts = Instant.parse("2026-08-19T16:30:00Z").toEpochMilli()
    private val expectedDate =
        Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).toLocalDate().toString()

    private fun record(weight: Double, log: String = "") =
        Record(weight = weight, log = log, timestamp = ts)

    @Test
    fun `记录行包含日期 体重和日志`() {
        val prompt = AnalysisPromptBuilder.build(
            records = listOf(record(75.46, "晚上跑了三公里")),
            scopeLabel = "近7天",
            bmi = 23.5,
            heightCm = 175.0,
            targetWeight = 70.0,
        )
        assertTrue("应包含日期和保留一位小数的体重，实际：\n$prompt", "- $expectedDate: 75.5kg [日志: 晚上跑了三公里]" in prompt)
    }

    @Test
    fun `无日志的记录不拼接日志段`() {
        val prompt = AnalysisPromptBuilder.build(
            records = listOf(record(75.0, log = "")),
            scopeLabel = "近7天",
            bmi = 23.5,
            heightCm = 175.0,
            targetWeight = 70.0,
        )
        assertFalse("空日志不应出现日志标记", "日志:" in prompt)
        assertTrue("- $expectedDate: 75.0kg" in prompt)
    }

    @Test
    fun `档案字段与所选范围写入 Prompt`() {
        val prompt = AnalysisPromptBuilder.build(
            records = listOf(record(75.0), record(74.6)),
            scopeLabel = "近3月",
            bmi = 24.1,
            heightCm = 180.0,
            targetWeight = 68.5,
        )
        assertTrue("身高未写入", "身高：180.0cm" in prompt)
        assertTrue("目标体重未写入", "目标体重：68.5kg" in prompt)
        assertTrue("BMI 未写入", "当前BMI：24.1" in prompt)
        // 范围标签在档案与回复要求中都应出现，保证 AI 的时间前提与取数一致
        assertEquals(2, prompt.split("近3月").size - 1)
    }

    @Test
    fun `热量数据写入 Prompt 且洞察要求提及饮食关联`() {
        val prompt = AnalysisPromptBuilder.build(
            records = listOf(record(75.0), record(75.4)),
            scopeLabel = "近7天",
            bmi = 24.1,
            heightCm = 178.0,
            targetWeight = 70.0,
            dailyCalories = listOf(
                DailyCalories("2026-08-14", 1850),
                DailyCalories("2026-08-15", 2100),
            ),
        )
        assertTrue("热量段落未输出", "【热量摄入】" in prompt)
        assertTrue("逐日明细未写入", "- 2026-08-14: 1850 kcal" in prompt)
        assertTrue("逐日明细未写入", "- 2026-08-15: 2100 kcal" in prompt)
        assertTrue("洞察要求未提及热量关联", "热量摄入与体重变化的关系" in prompt)
    }

    @Test
    fun `无热量数据时不输出热量段落`() {
        val prompt = AnalysisPromptBuilder.build(
            records = listOf(record(75.0), record(74.6)),
            scopeLabel = "近7天",
            bmi = 23.5,
            heightCm = 175.0,
            targetWeight = 70.0,
            dailyCalories = emptyList(),
        )
        assertFalse("空数据不应出现热量段落", "【热量摄入】" in prompt)
    }

    @Test
    fun `Health Connect 摘要写入 Prompt 且限制因果推断`() {
        val prompt = AnalysisPromptBuilder.build(
            records = listOf(record(75.0), record(74.6)),
            scopeLabel = "近7天",
            bmi = 23.5,
            heightCm = 175.0,
            targetWeight = 70.0,
            healthSummary = HealthActivitySummary(
                rangeDays = 7,
                steps = 56_000,
                totalCaloriesBurned = 14_000,
                sleepMinutes = 3_360,
            ),
        )
        assertTrue("活动与恢复段落未输出", "【活动与恢复】" in prompt)
        assertTrue("日均步数计算错误", "日均步数：8000 步" in prompt)
        assertTrue("日均总消耗计算错误", "日均总消耗：2000 kcal" in prompt)
        assertTrue("日均睡眠计算错误", "日均睡眠：8小时0分钟" in prompt)
        assertTrue("应限制 AI 将相关性描述为因果", "不要把相关性描述为确定的因果关系" in prompt)
    }

    @Test
    fun `无 Health Connect 摘要时不输出活动段落`() {
        val prompt = AnalysisPromptBuilder.build(
            records = listOf(record(75.0), record(74.6)),
            scopeLabel = "近7天",
            bmi = 23.5,
            heightCm = 175.0,
            targetWeight = 70.0,
        )
        assertFalse("空数据不应出现活动段落", "【活动与恢复】" in prompt)
    }

    @Test
    fun `低可信趋势写入 Prompt 并限制过度解读`() {
        val trend = WeightTrendAnalyzer.analyze(
            dailyWeights = listOf(
                DailyWeight(75.0, "2026-08-18", ts),
                DailyWeight(74.8, "2026-08-19", ts + 86_400_000L),
            ),
            totalDays = 7,
            targetWeight = 70.0,
        )
        val prompt = AnalysisPromptBuilder.build(
            records = listOf(record(75.0), record(74.8)),
            scopeLabel = "近7天",
            bmi = 23.5,
            heightCm = 175.0,
            targetWeight = 70.0,
            trendInsight = trend,
        )

        assertTrue("趋势信号未输出", "【趋势信号】" in prompt)
        assertTrue("低可信限制未输出", "不得断言已进入平台期" in prompt)
        assertTrue("应披露可信度", "数据可信度：可信度较低" in prompt)
    }

    @Test
    fun `超过62天时热量按月聚合日均`() {
        // 构造 70 天数据（跨 6、7 两月），触发按月聚合
        val daily = (0 until 70).map { offset ->
            val date = LocalDate.of(2026, 6, 1).plusDays(offset.toLong()).toString()
            DailyCalories(date, 1800)
        }
        val prompt = AnalysisPromptBuilder.build(
            records = listOf(record(75.0), record(74.6)),
            scopeLabel = "近3月",
            bmi = 23.5,
            heightCm = 175.0,
            targetWeight = 70.0,
            dailyCalories = daily,
        )
        assertTrue("应按月输出日均", "- 2026-06: 日均 1800 kcal" in prompt)
        assertTrue("应按月输出日均", "- 2026-07: 日均 1800 kcal" in prompt)
        assertFalse("不应再输出逐日明细", "- 2026-06-01: 1800 kcal" in prompt)
    }

    @Test
    fun `超过62条时体重记录按月聚合`() {
        // 构造 63 条记录（每天一条，跨 6、7、8 三月），触发按月聚合
        val records = (0 until 63).map { offset ->
            val date = LocalDate.of(2026, 6, 1).plusDays(offset.toLong())
            Record(
                weight = 80.0,
                log = "",
                timestamp = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            )
        }
        val prompt = AnalysisPromptBuilder.build(
            records = records,
            scopeLabel = "2026年",
            bmi = 25.0,
            heightCm = 175.0,
            targetWeight = 70.0,
        )
        assertTrue("应按月输出聚合行", "- 2026-06: 打卡 30 天，平均 80.0kg，最高 80.0kg，最低 80.0kg" in prompt)
        assertTrue("应按月输出聚合行", "- 2026-07: 打卡 31 天，平均 80.0kg，最高 80.0kg，最低 80.0kg" in prompt)
        assertTrue("应按月输出聚合行", "- 2026-08: 打卡 2 天，平均 80.0kg，最高 80.0kg，最低 80.0kg" in prompt)
        assertFalse("不应再输出逐条日期明细", "日志:" in prompt)
    }

    @Test
    fun `个人档案补充字段写入且未设置时不写入`() {
        val withProfile = AnalysisPromptBuilder.build(
            records = listOf(record(75.0), record(74.6)),
            scopeLabel = "近7天",
            bmi = 23.5,
            heightCm = 178.0,
            targetWeight = 70.0,
            age = 30,
            genderLabel = "男",
            activityLabel = "中度活动（每周运动3-5次）",
        )
        assertTrue("年龄未写入", "- 年龄：30岁" in withProfile)
        assertTrue("性别未写入", "- 性别：男" in withProfile)
        assertTrue("活动水平未写入", "- 活动水平：中度活动（每周运动3-5次）" in withProfile)

        val withoutProfile = AnalysisPromptBuilder.build(
            records = listOf(record(75.0), record(74.6)),
            scopeLabel = "近7天",
            bmi = 23.5,
            heightCm = 178.0,
            targetWeight = 70.0,
        )
        assertFalse("未设置时不应出现年龄行", "年龄：" in withoutProfile)
        assertFalse("未设置时不应出现性别行", "性别：" in withoutProfile)
        assertFalse("未设置时不应出现活动水平行", "活动水平：" in withoutProfile)
    }
}
