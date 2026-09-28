package com.example.weight.data.chat

import com.example.weight.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.koin.core.annotation.Single
import java.io.InterruptedIOException

/** 服务端返回非 2xx、或响应体不是合法聊天结果时抛出，message 可直接展示给用户 */
class ChatApiException(message: String) : Exception(message)

/**
 *@description: AI 聊天远程数据源（豆包/火山方舟），OkHttp 直连 + kotlinx.serialization
 *@author: 杨帅林
 *@create: 2025/10/4 15:16
 **/
@Single
class ChatRemoteDataSource(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
) {
    companion object {
        private const val BASE_URL = "https://ark.cn-beijing.volces.com/api/v3"
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }

    private val modelId: String
        get() = com.example.weight.data.LocalStorageData.doubaoModelId.value
            .ifBlank { ChatModel.DOUBAO_SEED_2_0_LITE.value }

    // ==================== 文本聊天（OpenAI 兼容格式） ====================

    suspend fun chat(
        model: ChatBodyModel,
        onMessage: (MessageModel) -> Unit,
    ) {
        val request = buildRequest(json.encodeToString(
            ChatBodyModel.serializer(),
            model.copy(model = modelId),
        ))
        try {
            val responseText = okHttpClient.newCall(request).executeCancellable { response ->
                val body = response.body.string()
                if (!response.isSuccessful) throw toApiException(response.code, body)
                json.parseToJsonElement(body).jsonObject
            }
            val choices = responseText["choices"]?.jsonArray
            choices?.let {
                it.singleOrNull()?.let { choice ->
                    val message = choice.jsonObject["message"]
                    message?.let {
                        onMessage(json.decodeFromJsonElement<MessageModel>(it))
                    }
                }
            }
        } catch (e: InterruptedIOException) {
            // 超时不再静默吞掉：向上抛出携带语义的异常，让调用方的 fallback 能记录/展示真实原因
            throw ChatApiException("AI 请求超时，请检查网络后重试")
        }
    }

    suspend fun streamChat(
        model: ChatBodyModel,
        onMessage: (StreamChunkResponse?) -> Unit,
    ) {
        val request = buildRequest(json.encodeToString(
            ChatBodyModel.serializer(),
            model.copy(stream = true, model = modelId),
        ))
        streamChat(request, onMessage)
    }

    // 请求构造与传输分开，允许 JVM 测试直接使用本地 SSE 服务，无需 Android 存储或 API key。
    internal suspend fun streamChat(
        request: Request,
        onMessage: (StreamChunkResponse?) -> Unit,
    ) {
        val context = currentCoroutineContext()
        try {
            okHttpClient.newCall(request).executeCancellable { response ->
                val source = response.body.source()
                if (!response.isSuccessful) {
                    throw toApiException(response.code, source.readUtf8())
                }
                while (true) {
                    context.ensureActive()
                    val line = source.readUtf8Line() ?: break
                    // 取消可能发生在阻塞读取期间；不再向 UI 发送已取消请求的 chunk。
                    context.ensureActive()
                    onMessage(parseChunk(line))
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: InterruptedIOException) {
            throw ChatApiException("AI 请求超时，请检查网络后重试")
        }
    }

    private fun buildRequest(bodyJson: String): Request = Request.Builder()
        .url("$BASE_URL/chat/completions")
        .post(bodyJson.toRequestBody(JSON_MEDIA_TYPE))
        .header("Authorization", "Bearer ${BuildConfig.DOUBAO_KEY}")
        .build()


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
    private fun toApiException(status: Int, body: String): ChatApiException {
        val detail = extractErrorMessage(body)
        return ChatApiException(detail ?: "请求失败（HTTP $status），请稍后重试")
    }

    /** 从 {"error":{"message":"..."}} 结构里提取服务端的错误描述 */
    private fun extractErrorMessage(payload: String): String? = try {
        json.parseToJsonElement(payload).jsonObject["error"]
            ?.jsonObject?.get("message")?.jsonPrimitive?.content
    } catch (e: Exception) {
        null
    }

}
