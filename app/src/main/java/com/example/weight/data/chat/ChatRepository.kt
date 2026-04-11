package com.example.weight.data.chat

import org.koin.core.annotation.Single

/**
 *@description: AI 聊天仓库（豆包/火山方舟）
 *@author: 杨帅林
 *@create: 2025/10/4 15:22
 **/
@Single
class ChatRepository(private val remoteDataSource: ChatRemoteDataSource) {

    suspend fun chat(model: ChatBodyModel, onMessage: (MessageModel) -> Unit) =
        remoteDataSource.chat(model, onMessage)

    suspend fun streamChat(model: ChatBodyModel, onMessage: (StreamChunkResponse?) -> Unit) =
        remoteDataSource.streamChat(model, onMessage)
}
