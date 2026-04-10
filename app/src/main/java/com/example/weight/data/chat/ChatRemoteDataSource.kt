package com.example.weight.data.chat

import android.util.Log
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
import io.ktor.utils.io.readUTF8Line
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.koin.core.annotation.Single

/**
 *@description:
 *@author: 杨帅林
 *@create: 2025/10/4 15:16
 **/
@Single
class ChatRemoteDataSource(private val httpClient: HttpClient, private val json: Json){
    suspend fun chat(model: ChatBodyModel, onMessage: (MessageModel) -> Unit){
        try {
            val responseText = httpClient.post(urlString = "https://api.deepseek.com/chat/completions") {
                method = HttpMethod.Companion.Post
                headers.append("Authorization","Bearer ${BuildConfig.DEEP_SEEK_KEY}")
                contentType(ContentType.Application.Json)
                setBody(model)
            }.body<JsonObject>()
            val choices = responseText["choices"]?.jsonArray
            choices?.let {
                choices.singleOrNull()?.let {i->
                    val message = i.jsonObject["message"]
                    message?.let {
                        onMessage(json.decodeFromJsonElement<MessageModel>(it))
                    }
                }
            }
        }catch (e: HttpRequestTimeoutException){
            e.printStackTrace()
        }
    }

    suspend fun streamChat(model: ChatBodyModel, onMessage: (StreamChunkResponse?) -> Unit){
        val body = json.encodeToString(ChatBodyModel.serializer(),model.copy(stream = true))
        httpClient.preparePost(urlString = "https://api.deepseek.com/chat/completions") {
            method = HttpMethod.Companion.Post
            headers.append("Authorization","Bearer ${BuildConfig.DEEP_SEEK_KEY}")
            contentType(ContentType.Application.Json)
            setBody(body)
        }.execute {httpResponse ->
            val channel: ByteReadChannel = httpResponse.bodyAsChannel()
            while (!channel.isClosedForRead) {
                val packet = channel.readUTF8Line()
                packet?.let {
                    onMessage(parseChunk(it))
                }
            }
        }
    }

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