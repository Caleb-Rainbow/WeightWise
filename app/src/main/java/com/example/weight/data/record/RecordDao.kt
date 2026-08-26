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

    // suspend：Room 自动调度到 IO 执行器，杜绝调用方忘包 withContext 时阻塞主线程的隐患
    @Query("SELECT * FROM Record ORDER BY id DESC LIMIT 1")
    suspend fun getLastData(): Record?

    @Query("SELECT * FROM Record ORDER BY timestamp DESC LIMIT 1")
    fun getLastDataFlow(): Flow<Record?>

    @Query("SELECT COUNT(*) FROM Record")
    fun getRecordCount(): Flow<Int>

    @Query("SELECT * FROM Record ORDER BY id asc LIMIT 1")
    suspend fun getFirstData(): Record?

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

    /** 成分趋势页取数：时间窗内含成分的记录（升序），空串手动记录排除；JSON 逐条解码与每日聚合在 [MetricTrend] 纯函数层完成 */
    @Query("SELECT timestamp, bodyComposition FROM Record WHERE timestamp >= :startTimeMillis AND bodyComposition != '' ORDER BY timestamp ASC")
    fun getCompositionsSince(startTimeMillis: Long): Flow<List<RecordCompositionRaw>>

    /**
     * 列级成分投影（迁移 11 起）：高频三率直接读列，不物化 JSON，
     * 供未来 SQL 层按体脂率/肌肉率/水分率过滤或排序的功能使用。
     * 三列之和 > 0 表示该行有成分数据（0.0=未测得，与 BodyComposition 的 0/空=未测得口径一致）。
     */
    @Query(
        "SELECT timestamp, fatRatio, muscleRatio, waterRatio FROM Record " +
            "WHERE timestamp >= :startTimeMillis AND fatRatio + muscleRatio + waterRatio > 0 ORDER BY timestamp ASC"
    )
    fun getMetricColumnsSince(startTimeMillis: Long): Flow<List<RecordMetricRaw>>

    @Query("SELECT * FROM Record WHERE timestamp >= :startTimeMillis")
    suspend fun getRecordWeightSince(startTimeMillis: Long): List<Record>

    /** 周期报告 AI 总结取数：[startMillis] 含、[endMillis] 排他，取原始记录（含日志） */
    @Query("SELECT * FROM Record WHERE timestamp >= :startMillis AND timestamp < :endMillis ORDER BY timestamp ASC")
    suspend fun getRecordWeightBetween(startMillis: Long, endMillis: Long): List<Record>

    @Query("SELECT * FROM Record ORDER BY timestamp ASC")
    suspend fun getAllOnce(): List<Record>

    /** 去重用轻量投影：只取 (timestamp, weight)，避免导入链路全量物化日志等大字段 */
    @Query("SELECT timestamp, weight FROM Record")
    suspend fun getDedupKeys(): List<RecordDedupKey>

    /** 全部打卡日（北京时间 yyyy-MM-dd，去重升序），供连续打卡计算 */
    @Query(
        "SELECT DISTINCT DATE(timestamp / 1000, 'unixepoch', '+8 hours') AS recordDay " +
            "FROM Record ORDER BY recordDay ASC"
    )
    fun getRecordDaysFlow(): Flow<List<String>>

}

/** 备份去重键：与 [BackupDeduplicator] 的 (timestamp, weight) 口径一致 */
data class RecordDedupKey(val timestamp: Long, val weight: Double)