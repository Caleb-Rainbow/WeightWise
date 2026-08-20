package com.example.weight.data.chat

import com.example.weight.data.record.Record
import java.time.Instant
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
}
