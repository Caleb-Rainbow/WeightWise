package com.example.weight.data.chat

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import okio.Buffer
import okio.ForwardingSource
import okio.buffer
import org.junit.Assert.*
import org.junit.Test
import java.io.Closeable
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/** JVM 集成测试：真实 OkHttp + 本地 HTTP/SSE socket，不需要设备、API key 或外网。 */
class ChatStreamCancellationTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test fun `parent cancellation interrupts waiting for response headers`() =
        assertPromptCancellation(prefix = null, blockedRead = null)

    @Test fun `parent cancellation closes response before first chunk`() =
        assertPromptCancellation(prefix = "", blockedRead = 1)

    @Test fun `parent cancellation interrupts silence between chunks`() =
        assertPromptCancellation(prefix = CHUNK, blockedRead = 2, expectedMessages = 1)

    @Test fun `parent cancellation interrupts an unfinished SSE line`() =
        assertPromptCancellation(prefix = "data: {\"id\":", blockedRead = 2)

    @Test fun `parent cancellation interrupts reading an HTTP error body`() =
        assertPromptCancellation(prefix = "", blockedRead = 1, status = 401)

    private fun assertPromptCancellation(
        prefix: String?,
        blockedRead: Int?,
        expectedMessages: Int = 0,
        status: Int = 200,
    ) = runBlocking {
        SseServer(prefix = prefix, status = status, stall = true).use { server ->
            val probe = ResponseProbe()
            val client = client(probe)
            // 与 viewModelScope 相同的父 Job 取消传播；I/O 在另一个线程真实阻塞。
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val result = CompletableDeferred<Throwable?>()
            val messages = AtomicInteger()
            val job = scope.launch {
                result.complete(runCatching {
                    ChatRemoteDataSource(client, json).streamChat(server.request) {
                        if (it != null) messages.incrementAndGet()
                    }
                }.exceptionOrNull())
            }
            try {
                withTimeout(5_000) {
                    server.requestReceived.await()
                    if (blockedRead != null) probe.awaitRead(blockedRead)
                }
                assertEquals(expectedMessages, messages.get())
                scope.cancel()
                // cancel() 返回时就应已取消对应 Call，不依赖 IO dispatcher 再调度。
                assertTrue("Call must be cancelled synchronously", probe.call.get().isCanceled())
                withTimeout(2_000) {
                    job.join()
                    assertTrue("Cancellation must not become a UI error", result.await() is CancellationException)
                    server.peerClosed.await()
                    if (blockedRead != null) probe.bodyClosed.await()
                }
                assertEquals(expectedMessages, messages.get())
                assertEquals(0, client.dispatcher.runningCallsCount())
            } finally {
                scope.cancel()
                client.dispatcher.cancelAll()
                client.connectionPool.evictAll()
            }
        }
    }

    @Test fun `read timeout retains user facing timeout message`() = runBlocking {
        SseServer(prefix = CHUNK, stall = true).use { server ->
            val probe = ResponseProbe()
            val client = client(probe).newBuilder().readTimeout(200, TimeUnit.MILLISECONDS).build()
            assertApiError(server, client, "AI 请求超时，请检查网络后重试")
            assertTrue(probe.bodyClosed.isCompleted)
        }
    }

    @Test fun `response header timeout retains user facing timeout message`() = runBlocking {
        SseServer(prefix = null, stall = true).use { server ->
            val client = client(ResponseProbe()).newBuilder().readTimeout(200, TimeUnit.MILLISECONDS).build()
            assertApiError(server, client, "AI 请求超时，请检查网络后重试")
        }
    }

    @Test fun `call timeout retains user facing timeout message`() = runBlocking {
        SseServer(prefix = "", stall = true).use { server ->
            val probe = ResponseProbe()
            val client = client(probe).newBuilder().callTimeout(500, TimeUnit.MILLISECONDS).build()
            assertApiError(server, client, "AI 请求超时，请检查网络后重试")
            assertTrue(probe.bodyClosed.isCompleted)
        }
    }

    @Test fun `HTTP error retains server message`() = runBlocking {
        SseServer(status = 401, prefix = """{"error":{"message":"API key 无效"}}""").use { server ->
            val probe = ResponseProbe()
            assertApiError(server, client(probe), "API key 无效")
            assertTrue(probe.bodyClosed.isCompleted)
        }
    }

    @Test fun `HTTP error without JSON retains status fallback`() = runBlocking {
        SseServer(status = 503, prefix = "unavailable").use { server ->
            assertApiError(server, client(ResponseProbe()), "请求失败（HTTP 503），请稍后重试")
        }
    }

    @Test fun `SSE error retains server message and closes response`() = runBlocking {
        SseServer(prefix = "data: {\"error\":{\"message\":\"额度不足\"}}\n\n", stall = true).use { server ->
            val probe = ResponseProbe()
            assertApiError(server, client(probe), "额度不足")
            assertTrue(probe.bodyClosed.isCompleted)
        }
    }

    @Test fun `normal stream completes and closes response without cancelling call`() = runBlocking {
        SseServer(prefix = CHUNK + "data: [DONE]\n\n").use { server ->
            val probe = ResponseProbe()
            val messages = mutableListOf<String>()
            withTimeout(5_000) {
                ChatRemoteDataSource(client(probe), json).streamChat(server.request) { chunk ->
                    chunk?.choices?.single()?.delta?.content?.let(messages::add)
                }
            }
            assertEquals(listOf("hello"), messages)
            assertTrue(probe.bodyClosed.isCompleted)
            assertFalse(probe.call.get().isCanceled())
        }
    }

    @Test fun `callback failure closes response and preserves original exception`() = runBlocking {
        SseServer(prefix = CHUNK, stall = true).use { server ->
            val probe = ResponseProbe()
            val expected = IllegalStateException("callback failed")
            val failure = withTimeout(5_000) {
                runCatching {
                    ChatRemoteDataSource(client(probe), json).streamChat(server.request) { throw expected }
                }.exceptionOrNull()
            }
            // 协程堆栈恢复可能复制异常，业务类型和消息应原样保留。
            assertEquals(expected.javaClass, failure?.javaClass)
            assertEquals(expected.message, failure?.message)
            assertTrue(probe.bodyClosed.isCompleted)
        }
    }

    @Test fun `already cancelled coroutine never executes call`() = runBlocking {
        val probe = ResponseProbe()
        val call = client(probe).newCall(Request.Builder().url("http://127.0.0.1:1/").build())
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.cancel()
        val entered = AtomicBoolean()
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            entered.set(true)
            call.executeCancellable { fail("Must not consume a response") }
        }.join()
        assertTrue(entered.get())
        assertFalse(call.isExecuted())
    }

    private suspend fun assertApiError(server: SseServer, client: OkHttpClient, message: String) {
        val error = withTimeout(5_000) {
            runCatching { ChatRemoteDataSource(client, json).streamChat(server.request) {} }.exceptionOrNull()
        }
        assertTrue("Expected ChatApiException, got $error", error is ChatApiException)
        assertEquals(message, error?.message)
    }

    private fun client(probe: ResponseProbe): OkHttpClient = OkHttpClient.Builder()
        // 取消测试必须远早于这两个超时完成。
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .eventListener(object : EventListener() {
            override fun callStart(call: Call) { probe.call.set(call) }
        })
        .addNetworkInterceptor { chain ->
            val response = chain.proceed(chain.request())
            val body = response.body
            val source = object : ForwardingSource(body.source()) {
                override fun read(sink: Buffer, byteCount: Long): Long {
                    probe.reads.trySend(Unit)
                    return super.read(sink, byteCount)
                }
                override fun close() {
                    try { super.close() } finally { probe.bodyClosed.complete(Unit) }
                }
            }.buffer()
            response.newBuilder().body(object : ResponseBody() {
                override fun contentType() = body.contentType()
                override fun contentLength() = body.contentLength()
                override fun source() = source
            }).build()
        }
        .build()

    private class ResponseProbe {
        val call = AtomicReference<Call>()
        val reads = Channel<Unit>(Channel.UNLIMITED)
        val bodyClosed = CompletableDeferred<Unit>()
        suspend fun awaitRead(count: Int) { repeat(count) { reads.receive() } }
    }

    /** 发出指定前缀后保持连接静默，直到客户端关闭；null 表示连响应头也不发送。 */
    private class SseServer(
        prefix: String?,
        status: Int = 200,
        stall: Boolean = false,
    ) : Closeable {
        private val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        private val socket = AtomicReference<Socket>()
        val requestReceived = CompletableDeferred<Unit>()
        val peerClosed = CompletableDeferred<Unit>()
        val request: Request = Request.Builder().url("http://127.0.0.1:${server.localPort}/chat/completions").build()
        private val worker = thread(name = "test-sse-server", isDaemon = true) {
            try {
                server.accept().use { connection ->
                    socket.set(connection)
                    val input = connection.getInputStream().bufferedReader()
                    while (!input.readLine().isNullOrEmpty()) { /* GET request headers */ }
                    requestReceived.complete(Unit)
                    if (prefix != null) {
                        val output = connection.getOutputStream()
                        output.write(("HTTP/1.1 $status Test\r\n" +
                            "Content-Type: text/event-stream\r\nConnection: close\r\n\r\n" + prefix).toByteArray())
                        output.flush()
                    }
                    if (stall) {
                        try { while (input.read() != -1) { } } catch (_: IOException) { }
                        peerClosed.complete(Unit)
                    }
                }
            } catch (e: IOException) {
                requestReceived.completeExceptionally(e)
            }
        }

        override fun close() {
            server.close()
            socket.get()?.close()
            worker.join(2_000)
            check(!worker.isAlive) { "SSE test server did not stop" }
        }
    }

    private companion object {
        const val CHUNK = "data: {\"id\":\"test\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\"hello\"}}]}\n\n"
    }
}
