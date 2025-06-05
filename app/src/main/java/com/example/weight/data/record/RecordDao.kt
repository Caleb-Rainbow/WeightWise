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

    @Update
    suspend fun update(record: Record)

    @Delete
    suspend fun delete(record: Record)

    @Query("SELECT * FROM Record WHERE weight LIKE '%' || :query || '%' ORDER BY timestamp DESC")
    fun pagingSource(query: String): PagingSource<Int, Record>

    @Query("SELECT * FROM Record ORDER BY id DESC LIMIT 1")
    fun getLastData(): Record?

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
}