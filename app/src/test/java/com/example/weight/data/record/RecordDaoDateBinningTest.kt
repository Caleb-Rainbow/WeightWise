package com.example.weight.data.record

import android.content.Context
import androidx.paging.PagingSource
import androidx.room.Room
import com.example.weight.data.AppDataBase
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * RecordDao 日期口径回归：getWeightsRaw 的时间窗语义（start 含、end 排他）与
 * 「raw 查询 + DailyWeightAggregator」端到端归组（固定 +8 北京日，跨 UTC 日界用例
 * 以 [beijing] 构造、设备/CI 时区无关）。聚合四口径细节见 DailyWeightAggregatorTest。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class RecordDaoDateBinningTest {

    private lateinit var db: AppDataBase
    private lateinit var dao: RecordDao

    @Before
    fun setup() {
        val context: Context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(context, AppDataBase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.recordDao()
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun `跨UTC日界按北京日归组且MIN口径取最低`() = runBlocking {
        // 08-20 07:00 的 UTC 时刻落在 UTC 日 08-19；08-20 23:30 的 UTC 时刻仍是 08-20。
        // 两者都必须归入北京日 08-20，且该日取最低的 74.5
        insert(75.0, beijing(8, 19, 7, 0))
        insert(76.0, beijing(8, 20, 7, 0))
        insert(74.5, beijing(8, 20, 23, 30))
        insert(73.0, beijing(8, 21, 7, 0))

        val days = aggregateSince(0L, DailyStatMode.MIN)

        assertEquals(listOf("2026-08-19", "2026-08-20", "2026-08-21"), days.map { it.recordDay })
        assertEquals(listOf(75.0, 74.5, 73.0), days.map { it.value })
    }

    @Test
    fun `同日同重时取较早时间戳`() = runBlocking {
        val early = beijing(8, 20, 7, 0)
        val late = beijing(8, 20, 12, 0)
        insert(75.0, late)
        insert(75.0, early)

        val days = aggregateSince(0L, DailyStatMode.MIN)

        assertEquals(1, days.size)
        assertEquals(early, days[0].timestamp)
    }

    @Test
    fun `raw查询start含end排他`() = runBlocking {
        insert(75.0, beijing(8, 19, 23, 0))
        insert(74.5, beijing(8, 20, 7, 0))
        // end 边界瞬间（北京 08-21 00:00.000）属下一天，必须排除
        insert(73.0, beijing(8, 21, 0, 0))

        val raw = dao.getWeightsRaw(beijing(8, 20, 0, 0), beijing(8, 21, 0, 0)).first()

        assertEquals(listOf(74.5), raw.map { it.weight })
    }

    @Test
    fun `打卡日去重升序`() = runBlocking {
        insert(75.0, beijing(8, 20, 23, 30))
        insert(74.5, beijing(8, 20, 7, 0))
        insert(73.0, beijing(8, 19, 7, 0))

        val days = dao.getRecordDaysFlow().first()

        assertEquals(listOf("2026-08-19", "2026-08-20"), days)
    }

    @Test
    fun `分页日期搜索按北京日匹配`() = runBlocking {
        insert(75.0, beijing(8, 20, 7, 0))
        insert(74.5, beijing(8, 20, 23, 30))
        // UTC 日 08-20（北京 08-20 23:30 之后）实为北京 08-21，不应命中 "08-20"
        insert(73.0, beijing(8, 21, 7, 0))

        val page = dao.pagingSource("08-20")
            .load(PagingSource.LoadParams.Refresh(key = null, loadSize = 10, placeholdersEnabled = false))

        val records = (page as PagingSource.LoadResult.Page).data
        assertEquals(listOf(74.5, 75.0), records.map { it.weight })
    }

    private suspend fun insert(weight: Double, timestamp: Long) {
        dao.insert(Record(weight = weight, log = "", timestamp = timestamp))
    }

    /** 直连 raw 查询 + 聚合器（绕开依赖 MMKV 的 dailyWeightsSince 扩展，保持测试纯净） */
    private suspend fun aggregateSince(startMillis: Long, mode: DailyStatMode): List<DailyWeight> =
        DailyWeightAggregator.aggregate(dao.getWeightsRaw(startMillis, Long.MAX_VALUE).first(), mode)

    /** 北京时刻 → epoch millis；固定 +8 偏移，不依赖运行环境时区 */
    private fun beijing(month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDateTime.of(2026, month, day, hour, minute)
            .toInstant(ZoneOffset.ofHours(8))
            .toEpochMilli()
}
