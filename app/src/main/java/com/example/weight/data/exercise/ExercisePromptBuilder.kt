package com.example.weight.data.exercise

import com.example.weight.data.chat.ChatBodyModel
import com.example.weight.data.chat.ChatMessageRole
import com.example.weight.data.chat.MessageModel
import com.example.weight.data.chat.jsonType
import com.example.weight.data.record.DailyMinWeight
import com.example.weight.data.record.Record
import com.example.weight.util.TimeUtils

object ExercisePromptBuilder {

    fun buildPrompt(
        height: Double,
        currentWeight: Double,
        bmi: Double,
        targetWeight: Double,
        recentWeights: List<DailyMinWeight>,
        recentRecords: List<Record>,
        completionRate: Float,
        skippedExercises: List<SkipRecord>,
        isFirstTime: Boolean,
        blacklistTags: Set<String> = emptySet(),
        whitelistTags: Set<String> = emptySet(),
        scene: String = "",
    ): ChatBodyModel {
        val userContent = buildString {
            appendLine("你是一位专业、温暖且富有同理心的运动健康顾问，专门为减重新手制定运动计划。")
            appendLine("你不安排需要器械或场地的运动，只推荐日常生活中随时可做的活动。")
            appendLine()
            appendLine("【用户档案】")
            appendLine("- 身高：${height}cm")
            appendLine("- 当前体重：${currentWeight}kg")
            appendLine("- BMI：${"%.1f".format(bmi)}")
            appendLine("- 目标体重：${targetWeight}kg")
            appendLine()

            if (blacklistTags.isNotEmpty() || whitelistTags.isNotEmpty() || scene.isNotEmpty()) {
                appendLine("【⚠️ 用户运动偏好与身体限制 — 最高优先级，必须严格遵守】")
                appendLine()

                if (blacklistTags.isNotEmpty()) {
                    appendLine("【绝对禁止的运动类型】（致命错误避免：违反以下任何一条将被视为严重错误）")
                    blacklistTags.forEach { tag ->
                        val prohibition = when (tag) {
                            "不伤膝盖" -> "绝对禁止安排任何对膝盖有冲击的运动，包括但不限于：跑步、跳跃、深蹲、爬楼梯、跳绳。所有运动必须对膝盖零压力。"
                            "不要跑跳" -> "绝对禁止安排任何包含跑、跳动作的运动。包括慢跑、跳绳、开合跳、波比跳等。用户无法承受任何跑跳类动作。"
                            "无器械" -> "绝对禁止安排任何需要器械的运动。只能安排徒手或利用日常生活环境的运动。不能安排骑车、需要哑铃/弹力带的运动。"
                            "讨厌流汗" -> "用户非常不喜欢流汗，绝对禁止安排高强度、容易大量出汗的运动。所有运动强度必须控制在微汗或不出汗的水平。"
                            "不能大动作" -> "绝对禁止安排需要大幅度肢体动作的运动。所有运动必须是小范围、温和的动作。不能安排HIIT、大幅度跳跃或快速移动。"
                            else -> "绝对禁止：$tag。请严格遵守此限制。"
                        }
                        appendLine("- $prohibition")
                    }
                    appendLine()
                    val customTags = blacklistTags.filter { it !in BlacklistTags.ALL }
                    if (customTags.isNotEmpty()) {
                        appendLine("对于以上未在预定义列表中出现的用户自定义限制（如「生理期」、「腰间盘突出」等），请你作为健康专家运用专业知识进行严格审查和推断。例如「生理期」意味着绝对禁止倒立、剧烈跑跳、核心高度收紧的运动。不要因为本地没有明确映射就放松限制。")
                        appendLine()
                    }
                }

                if (whitelistTags.isNotEmpty()) {
                    appendLine("【用户偏好（应当优先满足）】")
                    whitelistTags.forEach { tag ->
                        val preference = when (tag) {
                            "喜欢散步" -> "用户喜欢散步类活动，在可能的情况下优先安排散步、走路类运动。"
                            "喜欢拉伸" -> "用户喜欢拉伸放松，在可能的情况下多安排拉伸类运动。"
                            "喜欢做家务" -> "用户喜欢通过做家务来活动身体，优先安排家务相关的NEAT运动。"
                            "轻度燃脂" -> "用户偏好轻度燃脂运动，优先选择低强度但有一定热量消耗的运动。"
                            "喜欢站立活动" -> "用户喜欢站立式活动，优先安排站立相关的运动。"
                            else -> "用户偏好：$tag，请优先安排相关运动。"
                        }
                        appendLine("- $preference")
                    }
                    appendLine()
                }

                if (scene.isNotEmpty()) {
                    appendLine("【运动场景】")
                    appendLine("- 用户偏好运动场景：$scene。请只安排适合在该场景进行的运动。")
                    appendLine()
                }

                appendLine("再次强调：以上限制条件的优先级高于一切其他建议。即使其他指标建议提升难度，也不得违反上述限制。")
                appendLine()
            }

            if (recentWeights.isNotEmpty()) {
                appendLine("【近期体重趋势】")
                recentWeights.forEach { w ->
                    appendLine("- ${w.recordDay}: ${w.minWeight}kg")
                }
                appendLine()
            }

            if (completionRate >= 0) {
                appendLine("【近期运动完成情况】")
                appendLine("- 近7天完成率：${"%.0f".format(completionRate * 100)}%")
                if (skippedExercises.isNotEmpty()) {
                    appendLine("- 跳过的运动及原因：")
                    skippedExercises.forEach { s ->
                        appendLine("  - ${s.date}: ${s.exerciseName} — 原因: ${s.reason}")
                    }
                }
                appendLine()
            }

            val logsWithContent = recentRecords.mapNotNull { r ->
                val log = r.log.trim()
                if (log.isNotEmpty()) {
                    val date = TimeUtils.convertMillisToDate(r.timestamp)
                    "$date: $log"
                } else null
            }
            if (logsWithContent.isNotEmpty()) {
                appendLine("【近期日志】（请仔细阅读，判断用户的身体和心理疲劳程度）")
                logsWithContent.forEach { appendLine(it) }
                appendLine()
            }

            appendLine("【关键指示】")
            appendLine("请阅读用户的近期日志，判断用户的疲劳和情绪状态：")
            appendLine("- 如果用户处于疲惫、生病或情绪低落，请务必输出 difficulty=\"轻松\"，多安排NEAT类别运动")
            appendLine("- 如果用户状态良好且连续多天高完成率，可适当提升难度")
            appendLine("- 如果用户昨天跳过了某项运动且原因是\"太难\"或\"没时间\"，今天不要安排类似项目，或将其时长减半")
            if (isFirstTime) {
                appendLine("- 这是用户第一次使用运动计划功能，请安排最轻松的入门运动")
            }
            appendLine()

            appendLine("【输出要求】")
            appendLine("严格返回以下JSON结构（不要添加任何其他字段）：")
            appendLine("""{"difficulty":"轻松|适中|挑战","encouragement":"一句温暖的鼓励语","exercises":[{"name":"运动名称","description":"简短描述怎么做","durationMinutes":10,"estimatedCalories":30,"category":"NEAT|CARDIO|STRETCHING|BODYWEIGHT","intensity":"LIGHT|MODERATE|VIGOROUS"}],"dailyTip":"一个健康小贴士"}""")
            appendLine()
            appendLine("每次安排3-4个运动，必须包含至少1个NEAT（日常活动）类别运动。")
            appendLine("所有运动都不应需要任何器械或特殊场地。")
            appendLine("请严格确保 durationMinutes 在 5-60 之间（整数），estimatedCalories 在 10-300 之间（整数）。不要生成明显不合理的数值。")
            appendLine()
            appendLine("如果用户的限制条件极其苛刻，导致几乎没有符合条件的运动，请在encouragement字段中温和地向用户说明这一情况，并提供你能想到的最轻量活动（如闭目养神、深呼吸、坐在椅子上伸展手臂），而不是生搬硬套或违反限制。")
        }

        return ChatBodyModel(
            messages = listOf(
                MessageModel(role = ChatMessageRole.USER.label, content = userContent)
            ),
            responseFormat = jsonType,
        )
    }

    data class SkipRecord(
        val date: String,
        val exerciseName: String,
        val reason: String,
    )
}
