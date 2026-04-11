package com.example.weight.data.chat

import com.example.weight.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.post
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readLine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.koin.core.annotation.Single

/**
 *@description: AI 聊天远程数据源（豆包/火山方舟）
 *@author: 杨帅林
 *@create: 2025/10/4 15:16
 **/
@Single
class ChatRemoteDataSource(
    private val httpClient: HttpClient,
    private val json: Json,
) {
    companion object {
        private const val BASE_URL = "https://ark.cn-beijing.volces.com/api/v3"
    }

    private val modelId: String
        get() = com.example.weight.data.LocalStorageData.doubaoModelId.value
            .ifBlank { ChatModel.DOUBAO_SEED_2_0_LITE.value }

    // ==================== 文本聊天（OpenAI 兼容格式） ====================

    suspend fun chat(
        model: ChatBodyModel,
        onMessage: (MessageModel) -> Unit,
    ) {
        try {
            val responseText = httpClient.post(urlString = "$BASE_URL/chat/completions") {
                method = HttpMethod.Post
                headers.append("Authorization", "Bearer ${BuildConfig.DOUBAO_KEY}")
                contentType(ContentType.Application.Json)
                setBody(model.copy(model = modelId))
            }.body<JsonObject>()
            val choices = responseText["choices"]?.jsonArray
            choices?.let {
                it.singleOrNull()?.let { choice ->
                    val message = choice.jsonObject["message"]
                    message?.let {
                        onMessage(json.decodeFromJsonElement<MessageModel>(it))
                    }
                }
            }
        } catch (e: HttpRequestTimeoutException) {
            e.printStackTrace()
        }
    }

    suspend fun streamChat(
        model: ChatBodyModel,
        onMessage: (StreamChunkResponse?) -> Unit,
    ) {
        val body = json.encodeToString(
            ChatBodyModel.serializer(),
            model.copy(stream = true, model = modelId),
        )
        httpClient.preparePost(urlString = "$BASE_URL/chat/completions") {
            method = HttpMethod.Post
            headers.append("Authorization", "Bearer ${BuildConfig.DOUBAO_KEY}")
            contentType(ContentType.Application.Json)
            setBody(body)
        }.execute { httpResponse ->
            val channel: ByteReadChannel = httpResponse.bodyAsChannel()
            while (!channel.isClosedForRead) {
                val packet = channel.readLine()
                packet?.let {
                    onMessage(parseChunk(it))
                }
            }
        }
    }


    // ==================== SSE 解析 ====================

    private fun parseChunk(response: String): StreamChunkResponse? {
        if (response.isBlank() || response.startsWith("data: [DONE]")) return null
        return try {
            json.decodeFromString<StreamChunkResponse>(response.replace("data: ", ""))
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

}
