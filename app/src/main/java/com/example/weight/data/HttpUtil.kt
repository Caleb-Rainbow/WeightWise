package com.example.weight.data

import com.example.weight.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.ANDROID
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

fun createDefaultHttpClient(json: Json): HttpClient {
    return HttpClient(OkHttp) {
        // 插件1：内容协商，用于自动序列化/反序列化 @Serializable 类
        install(ContentNegotiation) {
            json(json)
        }
        // 插件3：超时设置
        install(HttpTimeout) {
            // LLM 长响应需要整体放宽；连接/读超时收紧，弱网下快速失败而不是挂满 5 分钟
            requestTimeoutMillis = 300000
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 120_000
        }
        // 全量日志会把流式响应逐 chunk 打进 logcat（还含 API key），仅 debug 构建启用且不打 BODY
        if (BuildConfig.DEBUG) {
            install(Logging) {
                logger = Logger.ANDROID
                level = LogLevel.INFO
            }
        }
    }
}
