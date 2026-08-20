package com.example.weight.data.chat

import com.example.weight.data.diet.DailyCalories
import com.example.weight.data.record.Record
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
