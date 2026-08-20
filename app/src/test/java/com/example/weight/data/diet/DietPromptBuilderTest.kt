package com.example.weight.data.diet

import com.example.weight.data.chat.ContentPart
import com.example.weight.data.chat.MessageContent
import com.example.weight.data.chat.jsonType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DietPromptBuilderTest {

    @Test
    fun `图片识别 Prompt 为多模态并内嵌 base64 图片`() {
        val body = DietPromptBuilder.buildFoodRecognitionPrompt(
            imageBase64 = "QUJD",
            userNote = "只吃了一半",
            mealType = "午餐",
        )
        val content = body.messages.single().content
        assertTrue("应为 MultiPart 内容", content is MessageContent.MultiPart)
        val parts = (content as MessageContent.MultiPart).parts
        val imagePart = parts.filterIsInstance<ContentPart.ImageUrl>().single()
        assertEquals("data:image/jpeg;base64,QUJD", imagePart.url.url)
        assertEquals(jsonType, body.responseFormat)
    }

    @Test
    fun `纯文字 Prompt 包含描述与用餐类型`() {
        val body = DietPromptBuilder.buildTextOnlyPrompt(
            userDescription = "一碗牛肉面加煎蛋",
            mealType = "早餐",
        )
        val text = body.messages.single().content.text ?: ""
        assertTrue("应包含用户描述", "一碗牛肉面加煎蛋" in text)
        assertTrue("应包含用餐类型", "【用餐类型】早餐" in text)
    }

    @Test
    fun `图片与文字 Prompt 都声明 JSON 输出格式`() {
        val imageBody = DietPromptBuilder.buildFoodRecognitionPrompt(imageBase64 = "QUJD")
        val textBody = DietPromptBuilder.buildTextOnlyPrompt(userDescription = "豆浆油条")
        assertEquals(jsonType, imageBody.responseFormat)
        assertEquals(jsonType, textBody.responseFormat)
    }
}
