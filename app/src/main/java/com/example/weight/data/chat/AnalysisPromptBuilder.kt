package com.example.weight.data.chat

import com.example.weight.data.record.Record
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 体重 AI 分析的 Prompt 构建器。
 * 从 MainViewModel 抽出的纯函数，入参全部显式传入，便于单元测试。
 */
object AnalysisPromptBuilder {

    /**
     * @param records 数据库中按所选范围取出的记录列表（时间不限序）
     * @param scopeLabel 界面所选时间范围的展示名，须与取数范围一致，避免 AI 基于错误前提分析
     */
    fun build(
        records: List<Record>,
        scopeLabel: String,
        bmi: Double,
        heightCm: Double,
        targetWeight: Double,
    ): String {
        val recordsString = records.joinToString("\n") { record ->
            val date = Instant.ofEpochMilli(record.timestamp)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
                .format(DateTimeFormatter.ISO_LOCAL_DATE)
            val logText = if (record.log.isNotBlank()) " [日志: ${record.log}]" else ""
            "- $date: ${String.format(Locale.CHINA, "%.1f", record.weight)}kg$logText"
        }

        return """
你是一位专业、温暖且富有同理心的体重管理顾问。请根据用户的体重记录和日志，为TA提供一份简单易懂、具有鼓励性的分析反馈。

【用户档案】
- 身高：${heightCm}cm
- 目标体重：${targetWeight}kg
- 当前BMI：$bmi
- 数据时间范围：$scopeLabel

【打卡数据】
$recordsString

【回复要求】
请以亲切的朋友口吻直接与用户对话（称呼“你”），字数控制在300字左右，并严格按以下三个段落结构输出：

1. 阶段总结：用一两句话概括用户在这段时间（$scopeLabel）的体重变化趋势（如：稳步下降、遇到平台期、轻微波动等），并给予情绪上的肯定或安抚。
2. 数据洞察：结合体重数值的变化和用户的[日志]内容，分析可能的原因。如果日志提到了饮食/运动/情绪，请指出它们与体重变化的关联；如果没有日志，请基于纯数值趋势进行合理推断。
3. 行动建议：基于现状，给出1到2个具体、微小且容易执行的日常建议，帮助用户向 ${targetWeight}kg 的目标迈进。

【严格限制条件】
- 必须使用纯文本，绝对不要输出任何代码块、Markdown复杂表格或特殊图标。
- 语言必须通俗易懂，绝对不要使用生僻的医学术语。
- 不要机械地罗列或复述用户的数据，你的重点是“解读数据背后的意义”。
""".trimIndent()
    }
}
