package com.example.weight.data.record

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow


@Dao
interface RecordDao {
    @Insert
    suspend fun insert(record: Record)

    @Insert
    suspend fun insertAll(records: List<Record>)

    @Update
    suspend fun update(record: Record)

    @Delete
    suspend fun delete(record: Record)

    // 按日志内容或北京时间日期（yyyy-MM-dd，支持 2026、2026-08、08-20 等前缀/子串）搜索
    @Query(
        """
    SELECT * FROM Record
    WHERE log LIKE '%' || :query || '%'
       OR DATE(timestamp / 1000, 'unixepoch', '+8 hours') LIKE '%' || :query || '%'
    ORDER BY timestamp DESC
    """
    )
    fun pagingSource(query: String): PagingSource<Int, Record>

    @Query("SELECT * FROM Record ORDER BY id DESC LIMIT 1")
    fun getLastData(): Record?

    @Query("SELECT * FROM Record ORDER BY timestamp DESC LIMIT 1")
    fun getLastDataFlow(): Flow<Record?>

    @Query("SELECT COUNT(*) FROM Record")
    fun getRecordCount(): Flow<Int>

    @Query("SELECT * FROM Record ORDER BY id asc LIMIT 1")
    fun getFirstData(): Record?

    @Query("SELECT * FROM Record ORDER BY id asc LIMIT 1")
    fun getFirstDataFlow(): Flow<Record?>

    @Query(
        """
    SELECT
        t.weight AS minWeight,
        t.recordDay, -- 这个 recordDay 现在是北京时间的日期
        t.timestamp
    FROM (
        SELECT
            weight,
            DATE(timestamp / 1000, 'unixepoch', '+8 hours') AS recordDay, -- 按北京时间提取日期
            timestamp,
            ROW_NUMBER() OVER (PARTITION BY DATE(timestamp / 1000, 'unixepoch', '+8 hours') ORDER BY weight ASC, timestamp ASC) as rn -- 按北京时间分区
        FROM Record
        WHERE timestamp >= :startTimeMillis
    ) AS t
    WHERE t.rn = 1
    ORDER BY t.recordDay ASC
"""
    )
    fun getDailyMinWeightSince(startTimeMillis: Long): Flow<List<DailyMinWeight>>

    /** 周期报告取数：[startMillis] 含、[endMillis] 排他（周期结束次日零点），保证翻历史周期不混入之后的数据 */
    @Query(
        """
    SELECT
        t.weight AS minWeight,
        t.recordDay,
        t.timestamp
    FROM (
        SELECT
            weight,
            DATE(timestamp / 1000, 'unixepoch', '+8 hours') AS recordDay,
            timestamp,
            ROW_NUMBER() OVER (PARTITION BY DATE(timestamp / 1000, 'unixepoch', '+8 hours') ORDER BY weight ASC, timestamp ASC) as rn
        FROM Record
        WHERE timestamp >= :startMillis AND timestamp < :endMillis
    ) AS t
    WHERE t.rn = 1
    ORDER BY t.recordDay ASC
"""
    )
    fun getDailyMinWeightBetween(startMillis: Long, endMillis: Long): Flow<List<DailyMinWeight>>

    @Query("SELECT * FROM Record WHERE timestamp >= :startTimeMillis")
    suspend fun getRecordWeightSince(startTimeMillis: Long): List<Record>

    /** 周期报告 AI 总结取数：[startMillis] 含、[endMillis] 排他，取原始记录（含日志） */
    @Query("SELECT * FROM Record WHERE timestamp >= :startMillis AND timestamp < :endMillis ORDER BY timestamp ASC")
    suspend fun getRecordWeightBetween(startMillis: Long, endMillis: Long): List<Record>

    @Query("SELECT * FROM Record ORDER BY timestamp ASC")
    suspend fun getAllOnce(): List<Record>

    /** 全部打卡日（北京时间 yyyy-MM-dd，去重升序），供连续打卡计算 */
    @Query(
        "SELECT DISTINCT DATE(timestamp / 1000, 'unixepoch', '+8 hours') AS recordDay " +
            "FROM Record ORDER BY recordDay ASC"
    )
    fun getRecordDaysFlow(): Flow<List<String>>

}