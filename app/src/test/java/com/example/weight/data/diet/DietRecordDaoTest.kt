package com.example.weight.data.diet

import android.app.Application
import androidx.room.Room
import com.example.weight.data.AppDataBase
import com.example.weight.ui.diet.DietHistoryData
import com.example.weight.ui.diet.dietHistoryData
import com.example.weight.ui.diet.savedMessage
import com.example.weight.util.ReportType
import com.example.weight.util.TimeUtils
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.TimeZone
import java.util.concurrent.Executor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
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
@OptIn(ExperimentalCoroutinesApi::class)
class DietRecordDaoTest {

    private lateinit var db: AppDataBase
    private lateinit var dao: DietRecordDao
    private lateinit var originalZone: TimeZone

    /** 相对今天偏移 N 天的 yyyy-MM-dd */
    private fun day(offsetDays: Long) = LocalDate.of(2026, 8, 20).plusDays(offsetDays).toString()

    @Before
    fun setup() {
        originalZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
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
        try {
            db.close()
        } finally {
            TimeZone.setDefault(originalZone)
        }
    }

    @Test
    fun `非东八区设备饮食历史和今日提示使用北京日`() = runTest {
        listOf("2026-08-17", "2026-08-18", "2026-08-20", "2026-08-21").forEach {
            dao.insert(record(it, "[]"))
        }
        for (zone in listOf("UTC", "America/Los_Angeles", "Pacific/Kiritimati")) {
            TimeZone.setDefault(TimeZone.getTimeZone(zone))
            val clock = Clock.fixed(Instant.parse("2026-08-19T16:00:00Z"), ZoneId.systemDefault())
            val today = TimeUtils.beijingToday(clock)
            val history = dietHistoryData(dao, MutableStateFlow(today.toString()), MutableStateFlow(3)).first()
            assertEquals(listOf("2026-08-20", "2026-08-19", "2026-08-18"), history.dates)
            assertEquals(listOf("2026-08-20", "2026-08-18"), history.records.map { it.date })
            assertEquals(1, dao.getByDate(today.toString()).first().size)
            assertEquals("已记录,今日还可摄入 500 kcal", savedMessage("2026-08-20", today, 500))
            assertEquals("已补记 昨天", savedMessage("2026-08-19", today, null))
        }
    }

    @Test
    fun `北京跨日只刷新今天也会滑动历史查询和空档日`() = runTest {
        dao.insert(record("2026-08-18", "[]"))
        dao.insert(record("2026-08-20", "[]"))
        val today = MutableStateFlow("2026-08-19")
        val range = MutableStateFlow(2)
        val emissions = mutableListOf<DietHistoryData>()
        val job = launch { dietHistoryData(dao, today, range).take(3).toList(emissions) }
        advanceUntilIdle()
        assertEquals(listOf("2026-08-19", "2026-08-18"), emissions.last().dates)
        assertEquals(listOf("2026-08-18"), emissions.last().records.map { it.date })

        // 模拟 ON_RESUME 的北京日更新；没有数据库写入和范围切换。
        today.value = "2026-08-20"
        advanceUntilIdle()
        assertEquals(listOf("2026-08-20", "2026-08-19"), emissions.last().dates)
        assertEquals(listOf("2026-08-20"), emissions.last().records.map { it.date })

        range.value = 3
        advanceUntilIdle()
        assertEquals(listOf("2026-08-20", "2026-08-19", "2026-08-18"), emissions.last().dates)
        assertEquals(listOf("2026-08-20", "2026-08-18"), emissions.last().records.map { it.date })
        job.join()
    }

    @Test
    fun `报告北京时间边界查询饮食不包含前一天且保留周期末日`() = runTest {
        listOf("2026-07-31", "2026-08-01", "2026-08-31", "2026-09-01").forEach {
            dao.insert(record(it, "[]").copy(estimatedCalories = 100))
        }
        val (start, end) = ReportType.MONTH.periodRange(LocalDate.of(2026, 8, 1))
        val calories = dao.getDailyCaloriesBetween(
            TimeUtils.beijingDate(start).toString(), TimeUtils.beijingDate(end).toString(),
        ).first()
        assertEquals(listOf("2026-08-01", "2026-08-31"), calories.map { it.date })
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
