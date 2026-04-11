package com.example.weight.data.chat

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

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
@Serializable
data class MessageModel(
    val id: String = "",
    @Transient
    val snowId: Long = 0,
    val role: String,
    val content: String,
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
