package com.example.weight

import android.app.Application
import android.graphics.Bitmap
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.weight.data.AppDataBase
import com.example.weight.data.LocalStorageData
import com.example.weight.data.diet.DietRecord
import com.example.weight.data.record.Record
import com.example.weight.data.sync.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** Run explicitly against an isolated local backend; never sends data to production. */
@RunWith(AndroidJUnit4::class)
class SyncDeviceTest {
    @Test fun offlineQueuePhotoSettingsConflictAndAccountSwitch() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("runSyncE2e") == "true" && BuildConfig.SYNC_SERVER_URL == "http://127.0.0.1:18083")
        val username = requireNotNull(args.getString("syncUser"))
        val second = requireNotNull(args.getString("syncSecondUser"))
        val password = requireNotNull(args.getString("syncPassword"))
        val application = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
        val db = Room.inMemoryDatabaseBuilder(application, AppDataBase::class.java).build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val repo = SyncRepository(application, db, scope)
        val stamp = UUID.randomUUID().toString()
        val image = application.filesDir.resolve("sync-test-$stamp.png")
        try {
            repo.initialize()
            val record = Record(weight = 81.25, log = "Device offline draft $stamp", timestamp = System.currentTimeMillis())
            val localId = db.recordDao().insert(record).toInt()
            assertEquals(1, db.recordDao().getAllOnce().size)
            assertEquals(0L, db.syncDao().weight(0, record.syncId)!!.revision)
            assertTrue(repo.synchronize()) // Anonymous data must remain local.
            assertEquals(0L, db.syncDao().weight(0, record.syncId)!!.revision)
            repo.login(username, password, false)
            val owner = repo.account.value.userId
            assertEquals(owner, db.recordDao().findForUpdate(localId)!!.ownerId)
            assertTrue(repo.synchronize())
            assertFalse(db.syncDao().weight(owner, record.syncId)!!.dirty)
            // A second client edits the same row while Android has an offline edit.
            val accepted = db.syncDao().weight(owner, record.syncId)!!
            db.recordDao().update(accepted.copy(weight = 82.0))
            val token = SessionCipher.decrypt(repo.account.value.encryptedToken)
            val payload = buildJsonObject { put("weight", 79.5); put("timestamp", accepted.timestamp); put("log", "Second client edit $stamp") }
            remote(token, SyncChange("weight", accepted.syncId, accepted.revision, UUID.randomUUID().toString(), payload = payload))
            assertTrue(repo.synchronize())
            assertEquals(79.5, db.syncDao().weight(owner, record.syncId)!!.weight, 0.0)
            val conflict = db.syncDao().conflicts(owner).first().first { it.kind == "weight" }
            repo.resolveConflict(conflict, true)
            assertTrue(db.recordDao().getAllOnce().any { it.syncId != record.syncId && it.weight == 82.0 })
            // A real PNG goes through the same queue and a clean client downloads it.
            image.outputStream().use { Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, it) }
            val meal = DietRecord(date = "2026-09-28", timestamp = System.currentTimeMillis(), mealType = "LUNCH", imageUri = image.absolutePath, userInput = "Device photo test", recognizedFoodJson = "[]", estimatedCalories = 520)
            db.dietRecordDao().insert(meal)
            LocalStorageData.height.value = 178.0
            LocalStorageData.targetWeight.value = 70.0
            assertTrue(repo.synchronize())
            assertEquals(0, db.syncDao().pending(owner).first())
            val deleted = db.syncDao().weight(owner, record.syncId)!!
            db.recordDao().delete(deleted)
            assertTrue(repo.synchronize())
            assertTrue(db.syncDao().weight(owner,record.syncId)!!.deleted)
            repo.logout()
            assertTrue(db.recordDao().getAllOnce().isEmpty())
            assertTrue(db.dietRecordDao().getAllOnce().isEmpty())
            repo.login(second,password,false)
            assertTrue(repo.synchronize())
            assertFalse(db.recordDao().getAllOnce().any { it.syncId == record.syncId })
            assertFalse(db.dietRecordDao().getAllOnce().any { it.syncId == meal.syncId })
            repo.logout()
            repo.login(username,password,false)
            assertEquals(owner,repo.account.value.userId)
            assertEquals(178.0,LocalStorageData.height.value,0.0)
            // New install simulation: no account data in a second local DB.
            val fresh = Room.inMemoryDatabaseBuilder(application,AppDataBase::class.java).build()
            try {
                val download = SyncRepository(application,fresh,scope)
                download.initialize();download.login(username,password,false)
                assertTrue(download.synchronize())
                val restored = fresh.syncDao().diet(owner,meal.syncId)!!
                assertTrue(java.io.File(restored.imageUri).isFile)
                assertArrayEquals(image.readBytes(),java.io.File(restored.imageUri).readBytes())
                assertTrue(fresh.syncDao().weight(owner,record.syncId)!!.deleted)
            } finally { fresh.close() }
        } finally { scope.cancel();db.close();image.delete();ActiveAccount.id = 0 }
    }

    private suspend fun remote(token: String, change: SyncChange) = withContext(Dispatchers.IO) {
        val json = Json { encodeDefaults = true }
        val req = Request.Builder().url("${BuildConfig.SYNC_SERVER_URL}/api/v1/apps/com.example.weight/weight/sync")
            .header("Authorization","Bearer $token").post(json.encodeToString(SyncRequest.serializer(),SyncRequest(0,listOf(change))).toRequestBody("application/json".toMediaType())).build()
        OkHttpClient().newCall(req).execute().use { assertTrue("remote edit failed: ${it.code}",it.isSuccessful) }
    }
}
