package com.example.weight.data.chat

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class ChatBodyModel(
    val messages: List<MessageModel>,
    @EncodeDefault
    val model: String = ChatModel.DOUBAO_SEED_2_0_PRO.value,
    @SerialName("prompt_id")
    @EncodeDefault
    val promptId: String = "",
    @EncodeDefault
    @SerialName("response_format")
    val responseFormat: Map<String, String> = textType,
    @EncodeDefault
    val stream: Boolean = false,
    @EncodeDefault
    val thinking: Map<String, String> = mapOf("type" to "disabled")
)

val jsonType = mapOf("type" to "json_object")
val textType = mapOf("type" to "text")

/**
 * 消息内容，支持纯文本和多模态（文本+图片）
 */
sealed class MessageContent {
    data class TextOnly(val textContent: String) : MessageContent()
    data class MultiPart(val parts: List<ContentPart>) : MessageContent()

    val text: String?
        get() = when (this) {
            is TextOnly -> textContent
            is MultiPart -> parts.filterIsInstance<ContentPart.Text>().firstOrNull()?.text
        }
}

@Serializable
sealed class ContentPart {
    @Serializable
    data class Text(
        @EncodeDefault
        val type: String = "text",
        val text: String,
    ) : ContentPart()

    @Serializable
    data class ImageUrl(
        @EncodeDefault
        val type: String = "image_url",
        @SerialName("image_url")
        val url: ImageUrlData,
    ) : ContentPart()
}

@Serializable
data class ImageUrlData(
    val url: String,
    val detail: String = "high",
)

/**
 * 自定义序列化器：
 * - TextOnly → 纯 JSON 字符串（向后兼容）
 * - MultiPart → JSON 数组
 * - 反序列化：同时处理字符串和数组格式
 */
object MessageContentSerializer : KSerializer<MessageContent> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("MessageContent", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: MessageContent) {
        val jsonEncoder = encoder as JsonEncoder
        when (value) {
            is MessageContent.TextOnly -> {
                jsonEncoder.encodeString(value.textContent)
            }
            is MessageContent.MultiPart -> {
                val json = kotlinx.serialization.json.Json
                val array = buildJsonArray {
                    value.parts.forEach { part ->
                        when (part) {
                            is ContentPart.Text -> add(json.encodeToJsonElement(ContentPart.Text.serializer(), part))
                            is ContentPart.ImageUrl -> add(json.encodeToJsonElement(ContentPart.ImageUrl.serializer(), part))
                        }
                    }
                }
                jsonEncoder.encodeJsonElement(array)
            }
        }
    }

    override fun deserialize(decoder: Decoder): MessageContent {
        val jsonDecoder = decoder as JsonDecoder
        val element = jsonDecoder.decodeJsonElement()
        return if (element is JsonPrimitive) {
            MessageContent.TextOnly(element.content)
        } else {
            val parts = element.jsonArray.map { jsonEl ->
                val obj = jsonEl.jsonObject
                when (obj["type"]?.jsonPrimitive?.content) {
                    "text", "input_text" -> {
                        val text = obj["text"]?.jsonPrimitive?.content ?: ""
                        ContentPart.Text(text = text)
                    }
                    "image_url", "input_image" -> {
                        val imageUrlElement = obj["image_url"]
                        // 兼容两种格式：直接字符串 或 嵌套对象 {"url": "..."}
                        val url = if (imageUrlElement is JsonPrimitive) {
                            imageUrlElement.content
                        } else {
                            imageUrlElement?.jsonObject?.get("url")?.jsonPrimitive?.content ?: ""
                        }
                        ContentPart.ImageUrl(url = ImageUrlData(url = url))
                    }
                    else -> ContentPart.Text(text = "")
                }
            }
            MessageContent.MultiPart(parts)
        }
    }
}

@Serializable
data class MessageModel(
    val id: String = "",
    @Transient
    val snowId: Long = 0,
    val role: String,
    @Serializable(with = MessageContentSerializer::class)
    val content: MessageContent,
)

enum class ChatModel(val value: String, val displayName: String) {
    DOUBAO_SEED_2_0_PRO("doubao-seed-2-0-pro-260215", "豆包 Seed 2.0 Pro"),
    DOUBAO_SEED_2_0_MINI("doubao-seed-2-0-mini-260215", "豆包 Seed 2.0 Mini"),
    DOUBAO_SEED_2_0_LITE("doubao-seed-2-0-lite-260215", "豆包 Seed 2.0 Lite"),
}

enum class ChatMessageRole(val role: Int, val label: String) {
    USER(0, "user"),
    ASSISTANT(1, "assistant"),
    TOOL(2, "tool");
    companion object {
        fun fromLabel(label: String): ChatMessageRole {
            return when (label) {
                USER.label -> USER
                ASSISTANT.label -> ASSISTANT
                TOOL.label -> TOOL
                else -> USER
            }
        }
    }
}
