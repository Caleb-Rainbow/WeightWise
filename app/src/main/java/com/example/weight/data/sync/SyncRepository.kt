package com.example.weight.data.sync

import android.app.Application
import android.util.Base64
import androidx.room.withTransaction
import com.example.weight.data.AppDataBase
import com.example.weight.data.record.Record
import com.example.weight.data.diet.DietRecord
import com.example.weight.data.update.UpdateConfig
import com.example.weight.data.widget.WidgetUpdater
import com.tencent.mmkv.MMKV
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.koin.core.annotation.Single
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

class SyncAuthException(message: String) : IOException(message)

@OptIn(ExperimentalCoroutinesApi::class)
@Single
class SyncRepository(private val application: Application, private val db: AppDataBase, private val appScope: CoroutineScope) {
    private val dao = db.syncDao()
    private val gate = Mutex()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).callTimeout(60, TimeUnit.SECONDS).build()
    private val base = "${com.example.weight.BuildConfig.SYNC_SERVER_URL}/api/v1/apps/${UpdateConfig.APP_KEY}"
    val account = MutableStateFlow(SyncAccount())
    val status = MutableStateFlow("离线数据保存在本机")
    val busy = MutableStateFlow(false)
    val pending = account.flatMapLatest { dao.pending(it.userId) }.stateIn(appScope, SharingStarted.Eagerly, 0)
    val conflicts = account.flatMapLatest { dao.conflicts(it.userId) }.stateIn(appScope, SharingStarted.Eagerly, emptyList())
    val lastSync = MutableStateFlow(0L)

    /** Called before any screen/worker is created, so constructors bind to the right owner. */
    fun initialize() = runBlocking(Dispatchers.IO) {
        val session = dao.session() ?: SyncAccount().also { dao.session(it) }
        ActiveAccount.id = session.userId
        account.value = session
        val preferencesOwner = MMKV.defaultMMKV().decodeLong(SyncStorage.settingsOwnerKey, 0)
        if (preferencesOwner != session.userId) restoreSettings(session.userId)
        captureSettings()
        lastSync.value = dao.profile(session.userId)?.lastSync ?: 0
        if (session.userId > 0) status.value = "等待同步"
    }

    @OptIn(FlowPreview::class)
    fun start() {
        appScope.launch { SyncSettings.changes().debounce(250).collect { gate.withLock { captureSettings() } } }
        appScope.launch {
            account.flatMapLatest { dao.pending(it.userId) }.debounce(1000).collect {
                if (it > 0 && account.value.userId > 0) SyncWorker.enqueue(application)
            }
        }
        SyncWorker.schedule(application)
    }

    private suspend fun captureSettings() {
        val owner = account.value.userId
        val payload = SyncSettings.snapshot().toString()
        val old = dao.profile(owner)
        if (old?.payload != payload) dao.profile((old ?: SyncProfile(owner)).copy(payload = payload, dirty = true, mutationId = UUID.randomUUID().toString()))
    }

    private suspend fun restoreSettings(owner: Long) {
        val profile = dao.profile(owner)
        SyncSettings.restore(profile?.payload?.let { json.parseToJsonElement(it).jsonObject } ?: JsonObject(emptyMap()))
        MMKV.defaultMMKV().encode(SyncStorage.settingsOwnerKey, owner)
        refreshReminders()
    }

    private fun refreshReminders() {
        val settings = com.example.weight.data.LocalStorageData
        if (settings.reminderEnabled.value) com.example.weight.data.reminder.ReminderScheduler.schedule(application) else com.example.weight.data.reminder.ReminderScheduler.cancel(application)
        if (settings.weeklyReportPushEnabled.value) com.example.weight.data.report.ReportPushScheduler.schedule(application) else com.example.weight.data.report.ReportPushScheduler.cancel(application)
    }

    suspend fun captcha(): JsonObject = withContext(Dispatchers.IO) { request("/user/captcha", null) }

    suspend fun login(username: String, password: String, register: Boolean, captchaId: String = "", captchaCode: String = "") = withContext(Dispatchers.IO) {
        val data = request(if (register) "/user/register" else "/user/login", buildJsonObject {
            put("username", username.trim()); put("password", password)
            if (register) { put("captchaId", captchaId); put("captchaCode", captchaCode) }
        })
        val user = data.getValue("user").jsonObject
        val owner = user.getValue("id").jsonPrimitive.long
        val session = SyncAccount(userId = owner, name = user["nickname"]?.jsonPrimitive?.content ?: username, encryptedToken = SessionCipher.encrypt(data.getValue("token").jsonPrimitive.content))
        gate.withLock { withContext(NonCancellable) {
            captureSettings()
            db.withTransaction {
                if (account.value.userId == 0L) {
                    dao.bindWeights(owner); dao.bindDiets(owner)
                    if (dao.profile(owner) == null) dao.profile((dao.profile(0) ?: SyncProfile(0)).copy(ownerId = owner, cursor = 0, revision = 0, dirty = true))
                }
                dao.session(session)
            }
            ActiveAccount.id = owner
            restoreSettings(owner)
            account.value = session
            lastSync.value = dao.profile(owner)?.lastSync ?: 0
            status.value = "已登录，等待同步"
            SyncWorker.enqueue(application)
        } }
        WidgetUpdater(application).notifyDataChanged()
        SyncWorker.enqueue(application)
    }

    suspend fun logout() = withContext(Dispatchers.IO) {
        gate.withLock { withContext(NonCancellable) {
            captureSettings()
            dao.session(SyncAccount())
            ActiveAccount.id = 0
            // Anonymous preferences start fresh after a binding, never inherit account data.
            dao.profile(SyncProfile(0, payload = "{}", dirty = false))
            restoreSettings(0)
            account.value = SyncAccount()
            lastSync.value = 0
            status.value = "已退出，账号数据仍保留在本机，重新登录可继续同步"
        } }
        WidgetUpdater(application).notifyDataChanged()
    }

    fun syncNow() { SyncWorker.enqueue(application); status.value = "已安排同步，联网后自动执行" }

    suspend fun synchronize(): Boolean = withContext(Dispatchers.IO) {
        gate.withLock {
            val session = account.value
            if (session.userId == 0L) return@withLock true
            busy.value = true; status.value = "正在同步"
            try {
                var token = runCatching { SessionCipher.decrypt(session.encryptedToken) }.getOrElse { throw SyncAuthException("登录凭证无法读取，请重新登录") }
                var initialPullDone = false
                repeat(100) {
                    captureSettings()
                val profile = dao.profile(session.userId)!!
                    val changes = dao.weights(session.userId).map { weightChange(it) }.toMutableList()
                    changes += dao.diets(session.userId).map { dietChange(it) }
                    if (profile.dirty && (initialPullDone || profile.revision > 0 || profile.payload != SyncSettings.defaults().toString())) changes += SyncChange("settings", "profile", profile.revision, profile.mutationId, payload = json.parseToJsonElement(profile.payload).jsonObject)
                    val result = request("/weight/sync", json.encodeToJsonElement(SyncRequest(profile.cursor, changes)).jsonObject, token) { renewed ->
                        token = renewed
                    }
                    val response = json.decodeFromJsonElement<SyncResponse>(result)
                    initialPullDone = true
                    captureSettings() // Includes preferences edited while the request was in flight.
                    val settingsBeforeMerge = SyncSettings.snapshot().toString()
                    db.withTransaction {
                        for (item in response.accepted) applyRemote(session.userId, item, accepted = true)
                        for (item in response.conflicts) applyRemote(session.userId, item)
                        for (item in response.changes) applyRemote(session.userId, item)
                        val current = dao.profile(session.userId)!!
                        dao.profile(current.copy(cursor = response.cursor, lastSync = System.currentTimeMillis()))
                        val renewed = session.copy(encryptedToken = SessionCipher.encrypt(token))
                        dao.session(renewed); account.value = renewed
                    }
                    val mergedProfile = dao.profile(session.userId)!!
                    withContext(Dispatchers.Main) {
                        if (SyncSettings.snapshot().toString() == settingsBeforeMerge) {
                            SyncSettings.restore(json.parseToJsonElement(mergedProfile.payload).jsonObject)
                        }
                    }
                    if (settingsBeforeMerge != SyncSettings.snapshot().toString()) refreshReminders()
                    // Any edit racing with the merge remains a fresh pending mutation.
                    captureSettings()
                    lastSync.value = dao.profile(session.userId)!!.lastSync
                    if (!response.hasMore && dao.pending(session.userId).first() == 0) {
                        status.value = if (dao.conflicts(session.userId).first().isEmpty()) "全部数据已同步" else "同步完成，有冲突副本待处理"
                        WidgetUpdater(application).notifyDataChanged()
                        return@withLock true
                    }
                }
                status.value = "数据较多，稍后继续同步"; false
            } catch (e: CancellationException) { throw e
            } catch (e: SyncAuthException) { status.value = e.message ?: "请重新登录"; true
            } catch (e: Exception) { status.value = "同步未完成：${e.message ?: "网络不可用"}；本地数据已保留"; false
            } finally { busy.value = false }
        }
    }

    private fun payload(element: JsonElement) = JsonObject(element.jsonObject.filterKeys { it !in setOf("id", "ownerId", "syncId", "revision", "mutationId", "dirty", "deleted", "imageUri") })
    private fun weightChange(r: Record) = SyncChange("weight", r.syncId, r.revision, r.mutationId, r.deleted, payload(json.encodeToJsonElement(r)))
    private fun dietChange(r: DietRecord): SyncChange {
        val data = payload(json.encodeToJsonElement(r)).toMutableMap()
        if (r.imageUri.isEmpty()) data["imageBase64"] = JsonPrimitive("")
        if (!r.deleted && r.imageUri.isNotEmpty()) {
            val file = File(r.imageUri)
            if (file.isFile) {
                require(file.length() <= 3 * 1024 * 1024) { "饮食照片超过 3MB，请压缩后重试" }
                data["imageBase64"] = JsonPrimitive(Base64.encodeToString(file.readBytes(), Base64.NO_WRAP))
            }
        }
        return SyncChange("diet", r.syncId, r.revision, r.mutationId, r.deleted, JsonObject(data))
    }

    private suspend fun applyRemote(owner: Long, remote: SyncChange, accepted: Boolean = false) {
        when (remote.kind) {
            "weight" -> {
                val old = dao.weight(owner, remote.id)
                if (old != null && remote.revision <= old.revision) return
                if (accepted && old != null && old.mutationId != remote.mutationId) { dao.weight(old.copy(revision = remote.revision)); return }
                if (old?.dirty == true && old.mutationId != remote.mutationId) dao.conflict(SyncConflict(ownerId = owner, kind = "weight", payload = json.encodeToString(weightChange(old))))
                val value = if (remote.deleted) old ?: Record(weight = 1.0, log = "", timestamp = 1) else json.decodeFromJsonElement<Record>(remote.payload)
                dao.weight(value.copy(id = old?.id ?: 0, ownerId = owner, syncId = remote.id, revision = remote.revision, mutationId = remote.mutationId, dirty = false, deleted = remote.deleted))
            }
            "diet" -> {
                val old = dao.diet(owner, remote.id)
                if (old != null && remote.revision <= old.revision) return
                if (accepted && old != null && old.mutationId != remote.mutationId) { dao.diet(old.copy(revision = remote.revision)); return }
                if (old?.dirty == true && old.mutationId != remote.mutationId) dao.conflict(SyncConflict(ownerId = owner, kind = "diet", payload = json.encodeToString(dietChange(old))))
                val value = if (remote.deleted) old ?: DietRecord(date = "1970-01-01", timestamp = 1, mealType = "", recognizedFoodJson = "") else json.decodeFromJsonElement<DietRecord>(remote.payload)
                val encoded = remote.payload["imageBase64"]?.jsonPrimitive?.contentOrNull
                val path = if (!encoded.isNullOrEmpty()) {
                    val bytes = Base64.decode(encoded, Base64.NO_WRAP)
                    require(bytes.size <= 3 * 1024 * 1024)
                    val hash = java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
                    val dir = File(application.filesDir, com.example.weight.util.ImageCompressor.DIET_IMAGE_DIR).apply { mkdirs() }
                    File(dir, "sync_${owner}_${remote.id}_$hash.jpg").also { if (!it.exists()) it.writeBytes(bytes) }.absolutePath
                } else ""
                dao.diet(value.copy(id = old?.id ?: 0, imageUri = path, ownerId = owner, syncId = remote.id, revision = remote.revision, mutationId = remote.mutationId, dirty = false, deleted = remote.deleted))
            }
            "settings" -> {
                val old = dao.profile(owner) ?: SyncProfile(owner, dirty = false)
                if (remote.revision <= old.revision) return
                if (accepted && old.mutationId != remote.mutationId) { dao.profile(old.copy(revision = remote.revision)); return }
                if (old.dirty && old.mutationId != remote.mutationId && (old.revision > 0 || old.payload != SyncSettings.defaults().toString())) dao.conflict(SyncConflict(ownerId = owner, kind = "settings", payload = json.encodeToString(SyncChange("settings", "profile", old.revision, old.mutationId, payload = json.parseToJsonElement(old.payload).jsonObject))))
                dao.profile(old.copy(payload = remote.payload.toString(), revision = remote.revision, mutationId = remote.mutationId, dirty = false))
            }
        }
    }

    suspend fun resolveConflict(conflict: SyncConflict, restore: Boolean) = withContext(Dispatchers.IO) {
        gate.withLock {
            require(conflict.ownerId == account.value.userId)
            db.withTransaction {
                if (restore) {
                    val saved = json.decodeFromString<SyncChange>(conflict.payload)
                    val id = UUID.randomUUID().toString()
                    when (saved.kind) {
                        "weight" -> if (saved.deleted) {
                            dao.weight(conflict.ownerId, saved.id)?.let { db.recordDao().delete(it) }
                        } else dao.weight(json.decodeFromJsonElement<Record>(saved.payload).copy(id = 0, ownerId = conflict.ownerId, syncId = id, mutationId = id, revision = 0, dirty = true))
                        "diet" -> if (saved.deleted) {
                            dao.diet(conflict.ownerId, saved.id)?.let { db.dietRecordDao().delete(it) }
                        } else {
                            // Use normal media materialization then mark the new copy for upload.
                            applyRemote(conflict.ownerId, saved.copy(id = id, revision = 1, mutationId = id))
                            dao.diet(dao.diet(conflict.ownerId, id)!!.copy(revision = 0, dirty = true))
                        }
                        "settings" -> dao.profile(dao.profile(conflict.ownerId)!!.copy(payload = saved.payload.toString(), mutationId = id, dirty = true))
                    }
                }
                dao.removeConflict(conflict.id, conflict.ownerId)
            }
            restoreSettings(conflict.ownerId)
        }
        SyncWorker.enqueue(application)
    }

    private fun request(path: String, body: JsonObject?, token: String = "", renewed: (String) -> Unit = {}): JsonObject {
        val req = Request.Builder().url(base + path).apply {
            if (token.isNotEmpty()) header("Authorization", "Bearer $token")
            if (body != null) post(body.toString().toRequestBody("application/json".toMediaType()))
        }.build()
        client.newCall(req).execute().use { res ->
            val result = runCatching { json.parseToJsonElement(res.body.string()).jsonObject }.getOrElse { throw IOException("服务器响应无效 (${res.code})") }
            val error = result["error"]?.jsonPrimitive?.content ?: "请求失败 (${res.code})"
            if (res.code == 401 || res.code == 403) throw SyncAuthException(error)
            if (!res.isSuccessful || result["success"]?.jsonPrimitive?.boolean != true) throw IOException(error)
            res.header("X-Renewed-Token")?.let(renewed)
            return result["data"]!!.jsonObject
        }
    }
}
