package com.example.weight.data.exercise

import com.example.weight.data.chat.ChatBodyModel
import com.example.weight.data.chat.ChatMessageRole
import com.example.weight.data.chat.MessageModel
import com.example.weight.data.chat.jsonType
import com.example.weight.data.record.DailyMinWeight

object JourneyPromptBuilder {

    fun buildPrompt(
        height: Double,
        currentWeight: Double,
        bmi: Double,
        targetWeight: Double,
        targetDays: Int,
        recentWeights: List<DailyMinWeight>,
        exercisePreferences: Set<String>,
        exerciseLimitations: Set<String>,
    ): ChatBodyModel {
        val userContent = buildString {
            appendLine("你是一位专业的减重运动规划师，擅长根据用户的身体状况和时间目标，科学地将减重计划划分为多个阶段。")
            appendLine()
            appendLine("【用户档案】")
            appendLine("- 身高：${height}cm")
            appendLine("- 当前体重：${currentWeight}kg")
            appendLine("- BMI：${"%.1f".format(bmi)}")
            appendLine("- 目标体重：${targetWeight}kg")
            appendLine("- 计划天数：${targetDays}天")
            appendLine("- 需要减重：${"%.1f".format(currentWeight - targetWeight)}kg")
            appendLine()

            if (recentWeights.isNotEmpty()) {
                appendLine("【近期体重趋势】")
                recentWeights.forEach { w ->
                    appendLine("- ${w.recordDay}: ${w.minWeight}kg")
                }
                appendLine()
            }

            if (exerciseLimitations.isNotEmpty()) {
                appendLine("【身体限制】")
                exerciseLimitations.forEach { appendLine("- $it") }
                appendLine()
            }

            if (exercisePreferences.isNotEmpty()) {
                appendLine("【运动偏好】")
                exercisePreferences.forEach { appendLine("- $it") }
                appendLine()
            }

            appendLine("【任务要求】")
            appendLine("请将${targetDays}天的减重计划划分为3-5个阶段，满足以下条件：")
            appendLine("1. 每个阶段不少于7天")
            appendLine("2. 阶段总天数必须恰好等于${targetDays}天，首尾衔接无间隔")
            appendLine("3. 每个阶段难度递进（从适应到高效再到巩固）")
            appendLine("4. 目标减重之和应约等于${"%.1f".format(currentWeight - targetWeight)}kg")
            appendLine("5. 所有运动均为徒手、无器械、日常生活中可完成的")
            appendLine()

            appendLine("【输出格式】")
            appendLine("严格返回以下JSON结构（不要添加任何其他字段）：")
            appendLine("""{"phases":[{"name":"阶段名称（中文，简洁有力）","description":"阶段描述（1-2句话说明本阶段重点）","startDay":1,"endDay":14,"targetWeightLoss":1.5,"focusAreas":["有氧","拉伸"],"difficultyLevel":1,"dailyCalorieDeficit":200,"dailyExerciseDuration":20}],"overallAdvice":"给用户的整体建议（温暖、鼓励性的，2-3句话）"}""")
            appendLine()
            appendLine("字段说明：")
            appendLine("- name: 阶段名称，如\"适应期\"、\"燃脂期\"、\"塑形期\"")
            appendLine("- startDay/endDay: 1-based，首阶段从1开始，末阶段以${targetDays}结束，相邻阶段无间隔")
            appendLine("- targetWeightLoss: 本阶段预期减重(kg)，各阶段之和应约等于总目标")
            appendLine("- focusAreas: 本阶段侧重运动类型标签数组（如[\"日常活动\",\"轻度有氧\",\"拉伸\"]）")
            appendLine("- difficultyLevel: 1=轻松, 2=适中, 3=挑战")
            appendLine("- dailyCalorieDeficit: 建议每日热量缺口(kcal)，科学合理即可")
            appendLine("- dailyExerciseDuration: 建议每日运动时长(分钟)，15-60之间")
        }

        return ChatBodyModel(
            messages = listOf(
                MessageModel(role = ChatMessageRole.USER.label, content = userContent)
            ),
            responseFormat = jsonType,
        )
    }
}
