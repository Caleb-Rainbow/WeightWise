package com.example.weight.data.chat

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Response
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** 取消绑定覆盖等待响应头和消费响应体的全过程，响应始终由读取线程关闭。 */
internal suspend fun <T> Call.executeCancellable(consume: (Response) -> T): T =
    withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { continuation ->
            // 在阻塞 I/O 前注册；取消处理器直接运行，不依赖读取线程或下一条 SSE。
            // 不能用调用方 Job 的普通 invokeOnCompletion：它会等阻塞读取结束才执行。
            continuation.invokeOnCancellation { cancel() }
            if (!continuation.isActive) return@suspendCancellableCoroutine
            try {
                val result = execute().use(consume)
                continuation.resume(result)
            } catch (e: Exception) {
                // 取消已经胜出时 continuation 保留 CancellationException，
                // 不会把 Call.cancel() 导致的 IOException 当作网络错误交给 UI。
                continuation.resumeWithException(e)
            }
        }
    }
