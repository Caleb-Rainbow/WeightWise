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
    val model : String = ChatModel.DEEP_SEEK_V3.value,
    @SerialName("prompt_id")
    @EncodeDefault
    val promptId: String = "",
    @EncodeDefault
    @SerialName("response_format")
    val responseFormat: Map<String, String> = textType,
    @EncodeDefault
    val stream: Boolean = false
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
enum class ChatModel(val value: String,val displayName: String) {
    DEEP_SEEK_V3("deepseek-chat", "DeepSeek V3")
}
enum class ChatMessageRole(val role: Int,val label: String) {
    USER(0,"user"),
    ASSISTANT(1,"assistant"),
    TOOL(2,"tool");
    companion object{
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