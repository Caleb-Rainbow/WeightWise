package com.example.weight.data.diet

import android.app.Application
import androidx.room.Room
import com.example.weight.data.AppDataBase
import java.time.LocalDate
import java.util.concurrent.Executor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** 常用食物聚合投影查询回归:日期窗口双端闭合、空 JSON 排除、表写操作 Flow 重发 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class DietRecordDaoTest {

    private lateinit var db: AppDataBase
    private lateinit var dao: DietRecordDao

    /** 相对今天偏移 N 天的 yyyy-MM-dd */
    private fun day(offsetDays: Long) = LocalDate.now().plusDays(offsetDays).toString()

    @Before
    fun setup() {
        // 直连 executor:Room 查询与失效通知同步执行,runTest 时间轴确定性推进
        val direct = Executor { it.run() }
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDataBase::class.java)
            .setQueryExecutor(direct)
            .setTransactionExecutor(direct)
            .allowMainThreadQueries()
            .build()
        dao = db.dietRecordDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun record(date: String, foodJson: String) = DietRecord(
        date = date,
        timestamp = System.currentTimeMillis(),
        mealType = "LUNCH",
        recognizedFoodJson = foodJson,
    )

    @Test
    fun `聚合窗口双端闭合且排除空JSON`() = runTest {
        dao.insert(record(day(-59), """[{"a":1}]"""))
        dao.insert(record(day(-60), """[{"older":1}]""")) // 下界外
        dao.insert(record(day(0), """[{"b":2}]"""))
        dao.insert(record(day(3), """[{"future":1}]"""))  // 上界外:未来补记不参与统计
        dao.insert(record(day(0), ""))                    // 空 JSON 排除

        val jsons = dao.getFoodJsonBetween(day(-59), day(0)).first()
        assertEquals(listOf("""[{"a":1}]""", """[{"b":2}]"""), jsons.sorted())
    }

    @Test
    fun `表任意写操作后Flow重发`() = runTest {
        dao.insert(record(day(0), """[{"a":1}]"""))
        val emissions = mutableListOf<List<String>>()
        val job = launch {
            dao.getFoodJsonBetween(day(-59), day(0)).take(2).toList(emissions)
        }
        advanceUntilIdle()

        dao.insert(record(day(0), """[{"b":2}]"""))
        advanceUntilIdle()

        assertEquals(2, emissions.size)
        assertEquals(2, emissions.last().size)
        job.cancel()
    }
}
