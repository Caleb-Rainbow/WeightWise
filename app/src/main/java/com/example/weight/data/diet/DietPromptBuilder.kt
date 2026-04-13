package com.example.weight.data.diet

import com.example.weight.data.chat.ChatBodyModel
import com.example.weight.data.chat.ChatMessageRole
import com.example.weight.data.chat.ContentPart
import com.example.weight.data.chat.ImageUrlData
import com.example.weight.data.chat.MessageContent
import com.example.weight.data.chat.MessageModel
import com.example.weight.data.chat.jsonType

/**
 *@description: 饮食识别 AI Prompt 构建器
 *@author: 杨帅林
 *@create: 2026/4/11
 **/
object DietPromptBuilder {

    fun buildFoodRecognitionPrompt(
        imageBase64: String,
        userNote: String = "",
        mealType: String = "",
    ): ChatBodyModel {
        val textContent = buildPromptText(userNote, mealType, hasImage = true)

        return ChatBodyModel(
            messages = listOf(
                MessageModel(
                    role = ChatMessageRole.USER.label,
                    content = MessageContent.MultiPart(
                        listOf(
                            ContentPart.ImageUrl(
                                url = ImageUrlData(url = "data:image/jpeg;base64,$imageBase64")
                            ),
                            ContentPart.Text(text = textContent),
                        )
                    )
                )
            ),
            responseFormat = jsonType,
        )
    }

    fun buildTextOnlyPrompt(
        userDescription: String,
        mealType: String = "",
    ): ChatBodyModel {
        val textContent = buildPromptText(userDescription, mealType, hasImage = false)

        return ChatBodyModel(
            messages = listOf(
                MessageModel(
                    role = ChatMessageRole.USER.label,
                    content = MessageContent.TextOnly(textContent)
                )
            ),
            responseFormat = jsonType,
        )
    }

    private fun buildPromptText(userNote: String, mealType: String, hasImage: Boolean): String {
        return buildString {
            appendLine("你是一位专业的营养师，擅长通过照片和文字描述识别食物并估算热量。")
            appendLine()
            appendLine("【任务】")
            if (hasImage) {
                appendLine("请分析这张食物照片，识别其中的所有食物/饮品，并估算每种食物的重量和热量。")
            } else {
                appendLine("请根据用户的文字描述，识别其中的所有食物/饮品，并估算每种食物的重量和热量。")
            }
            if (userNote.isNotBlank()) {
                appendLine()
                appendLine("【用户补充说明】")
                appendLine(userNote)
                appendLine()
                appendLine("请根据用户的补充说明调整估算。例如：")
                appendLine("- \"只吃了一半\" → 总热量按50%折算")
                appendLine("- \"碗底的没吃\" → 减少主食类热量约30%")
                appendLine("- \"加了两个煎蛋\" → 增加2个煎蛋的热量(约180kcal)")
            }
            if (mealType.isNotBlank()) {
                appendLine()
                appendLine("【用餐类型】$mealType")
            }
            appendLine()
            appendLine("【交通灯评级规则】")
            appendLine("- GREEN（绿灯）：热量合理、营养均衡的一餐（蔬菜丰富、蛋白质充足、少油少糖）")
            appendLine("- YELLOW（黄灯）：热量尚可但有改进空间（油稍多、蔬菜偏少、或碳水偏高）")
            appendLine("- RED（红灯）：热量过高或营养结构严重失衡（油炸食品、高糖饮品、暴饮暴食）")
            appendLine()
            appendLine("【输出格式】")
            appendLine("严格返回以下JSON结构，不要包含任何Markdown标记或多余文字：")
            appendLine("""{"foods":[{"name":"食物名称","estimatedCalories":150,"estimatedGrams":200,"category":"主食","isHealthy":true}],"totalCalories":350,"macros":{"protein":10,"carbs":15,"fat":12},"trafficLight":"GREEN","advice":"简短饮食建议（一句话，温暖鼓励的语气）","adjustedDescription":"调整说明（如有用户补充，说明如何调整的）"}""")
            appendLine()
            appendLine("【注意事项】")
            if (hasImage) {
                appendLine("- 如果照片中无法识别出食物，请在foods中返回空数组，totalCalories为0，并在advice中说明原因")
            } else {
                appendLine("- 如果文字描述无法识别出具体食物，请尽力推测并在advice中标注不确定性")
            }
            appendLine("- estimatedCalories必须是正整数，合理估算")
            appendLine("- 对于中餐，请考虑常见的烹饪用油（一盘家常菜通常含15-30g隐形油）")
            appendLine("- isHealthy判断依据：低油低糖高纤维为true")
            appendLine("- category可选值：主食、蔬菜、肉类、蛋奶、饮品、水果、零食、调味品、其他")
            appendLine("- 如果有餐具或容器参照，可以利用其大小辅助估算分量")
            appendLine("- 如果信息模糊，给出最佳猜测并在advice中标注不确定性")
        }
    }
}
