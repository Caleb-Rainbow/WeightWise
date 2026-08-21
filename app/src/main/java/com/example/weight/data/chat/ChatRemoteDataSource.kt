package com.example.weight.data.chat

import com.example.weight.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.post
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readLine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.koin.core.annotation.Single

/** 服务端返回非 2xx、或响应体不是合法聊天结果时抛出，message 可直接展示给用户 */
class ChatApiException(message: String) : Exception(message)

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
            val response = httpClient.post(urlString = "$BASE_URL/chat/completions") {
                method = HttpMethod.Post
                headers.append("Authorization", "Bearer ${BuildConfig.DOUBAO_KEY}")
                contentType(ContentType.Application.Json)
                setBody(model.copy(model = modelId))
            }
            val responseText = checkResponse(response).body<JsonObject>()
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
            // 超时不再静默吞掉：向上抛出携带语义的异常，让调用方的 fallback 能记录/展示真实原因
            throw ChatApiException("AI 请求超时，请检查网络后重试")
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
            checkResponse(httpResponse)
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
        val payload = response.trim().removePrefix("data:").trim()
        if (payload.isEmpty() || payload == "[DONE]") return null
        return try {
            json.decodeFromString<StreamChunkResponse>(payload)
        } catch (e: Exception) {
            // 非 chunk 结构（如认证失败等错误响应），提取服务端信息后向上抛，
            // 否则调用方等不到首帧内容，UI 会一直停留在加载态
            extractErrorMessage(payload)?.let { throw ChatApiException(it) }
            e.printStackTrace()
            null
        }
    }

    /** 非 2xx 响应统一转成携带服务端错误信息的异常 */
    private suspend fun checkResponse(response: HttpResponse): HttpResponse {
        if (response.status.isSuccess()) return response
        val body = try {
            response.bodyAsText()
        } catch (e: Exception) {
            ""
        }
        throw toApiException(response.status, body)
    }

    private fun toApiException(status: HttpStatusCode, body: String): ChatApiException {
        val detail = extractErrorMessage(body)
        return ChatApiException(detail ?: "请求失败（HTTP ${status.value}），请稍后重试")
    }

    /** 从 {"error":{"message":"..."}} 结构里提取服务端的错误描述 */
    private fun extractErrorMessage(payload: String): String? = try {
        json.parseToJsonElement(payload).jsonObject["error"]
            ?.jsonObject?.get("message")?.jsonPrimitive?.content
    } catch (e: Exception) {
        null
    }

}
