package com.example.weight.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

fun createDefaultHttpClient(json: Json): HttpClient {
    return HttpClient(CIO) {
        // 插件1：内容协商，用于自动序列化/反序列化 @Serializable 类
        install(ContentNegotiation) {
            json(json)
        }
        // 插件3：超时设置
        install(HttpTimeout) {
            requestTimeoutMillis = 300000
            connectTimeoutMillis = 300000
            socketTimeoutMillis = 300000
        }
    }
}