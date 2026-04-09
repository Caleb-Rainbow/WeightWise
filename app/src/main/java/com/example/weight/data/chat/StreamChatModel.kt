package com.example.weight.data.chat

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class StreamChunkResponse(
    val id: String,
    @SerialName("choices") val choices: List<StreamChoice>
)

@Serializable
data class StreamChoice(
    val index: Int,
    val delta: StreamDelta,
    @SerialName("finish_reason") val finishReason: String? = null
)

@Serializable
data class StreamDelta(
    val role: String? = null,
    val content: String? = null,
)