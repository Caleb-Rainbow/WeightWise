package com.example.weight.data.health

import android.app.Application
import androidx.room.Room
import com.example.weight.data.AppDataBase
import com.example.weight.data.diet.DietRecord
import com.example.weight.data.record.Record
import java.util.concurrent.Executor
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class HealthConnectPersistenceTest {

    private lateinit var db: AppDataBase

    @Before
    fun setup() {
        val direct = Executor { it.run() }
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDataBase::class.java)
            .setQueryExecutor(direct)
            .setTransactionExecutor(direct)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `体重同步查询排除外部来源且支持来源 id 与时间去重`() = runTest {
        val dao = db.recordDao()
        val localId = dao.insert(Record(weight = 70.0, log = "本地", timestamp = 10_000))
        dao.insert(
            Record(
                weight = 69.8,
                log = "来自 Health Connect",
                timestamp = 20_000,
                healthConnectId = "external-weight-id",
                healthConnectOrigin = "com.vendor.health",
            )
        )

        assertEquals(listOf(localId.toInt()), dao.getLocalRecordsForHealthConnect().map { it.id })
        assertEquals(69.8, dao.getByHealthConnectId("external-weight-id")?.weight ?: 0.0, 0.0)
        assertEquals(69.8, dao.findNearest(timestamp = 20_500, toleranceMillis = 1_000)?.weight ?: 0.0, 0.0)
        assertNull(dao.findNearest(timestamp = 22_000, toleranceMillis = 1_000))
    }

    @Test
    fun `饮食同步查询排除外部来源且支持来源 id 与时间去重`() = runTest {
        val dao = db.dietRecordDao()
        val localId = dao.insert(
            DietRecord(
                date = "2026-08-26",
                timestamp = 10_000,
                mealType = "LUNCH",
                recognizedFoodJson = "[]",
            )
        )
        dao.insert(
            DietRecord(
                date = "2026-08-26",
                timestamp = 20_000,
                mealType = "DINNER",
                recognizedFoodJson = "[]",
                healthConnectId = "external-nutrition-id",
                healthConnectOrigin = "com.vendor.health",
            )
        )

        assertEquals(listOf(localId.toInt()), dao.getLocalRecordsForHealthConnect().map { it.id })
        assertEquals("DINNER", dao.getByHealthConnectId("external-nutrition-id")?.mealType)
        assertEquals("DINNER", dao.findNearest(timestamp = 20_500, toleranceMillis = 1_000)?.mealType)
        assertNull(dao.findNearest(timestamp = 22_000, toleranceMillis = 1_000))
    }
}
