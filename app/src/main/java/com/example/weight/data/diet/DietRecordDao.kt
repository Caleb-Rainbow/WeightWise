package com.example.weight.data.diet

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 *@description: 饮食记录 DAO
 *@author: 杨帅林
 *@create: 2026/4/11
 **/
@Dao
interface DietRecordDao {

    @Insert
    suspend fun insert(record: DietRecord): Long

    @Insert
    suspend fun insertAll(records: List<DietRecord>)

    @Update
    suspend fun update(record: DietRecord)

    @Delete
    suspend fun delete(record: DietRecord)

    @Query("SELECT * FROM DietRecord WHERE date = :date ORDER BY timestamp ASC")
    fun getByDate(date: String): Flow<List<DietRecord>>

    @Query("SELECT COALESCE(SUM(estimatedCalories), 0) FROM DietRecord WHERE date = :date")
    suspend fun getDailyCalories(date: String): Int

    @Query("SELECT COALESCE(SUM(estimatedCalories), 0) FROM DietRecord WHERE date = :date")
    fun getDailyCaloriesFlow(date: String): Flow<Int>

    @Query("""
        SELECT trafficLight, COUNT(*) as count
        FROM DietRecord
        WHERE date = :date
        GROUP BY trafficLight
    """)
    suspend fun getTrafficLightSummary(date: String): List<TrafficLightCount>

    /** 周期报告取数：[startDate] 含、[endDate] 排他（周期结束次日，yyyy-MM-dd 字典序比较） */
    @Query(
        """
        SELECT date, SUM(estimatedCalories) AS calories
        FROM DietRecord
        WHERE date >= :startDate AND date < :endDate
        GROUP BY date
        ORDER BY date ASC
        """
    )
    fun getDailyCaloriesBetween(startDate: String, endDate: String): Flow<List<DailyCalories>>

    /** 周期内红绿灯评级分布（date 为 yyyy-MM-dd，endDate 排他） */
    @Query(
        """
        SELECT trafficLight, COUNT(*) as count
        FROM DietRecord
        WHERE date >= :startDate AND date < :endDate
        GROUP BY trafficLight
    """
    )
    fun getTrafficLightBetween(startDate: String, endDate: String): Flow<List<TrafficLightCount>>

    @Query("SELECT * FROM DietRecord WHERE date >= :sinceDate ORDER BY date ASC, timestamp ASC")
    suspend fun getRecordsSince(sinceDate: String): List<DietRecord>

    /** 范围内每日摄入热量合计（date 为 yyyy-MM-dd，可字典序比较），供 AI 分析融合饮食数据 */
    @Query(
        """
        SELECT date, SUM(estimatedCalories) AS calories
        FROM DietRecord
        WHERE date >= :sinceDate
        GROUP BY date
        ORDER BY date ASC
        """
    )
    suspend fun getDailyCaloriesSince(sinceDate: String): List<DailyCalories>

    @Query("SELECT * FROM DietRecord WHERE imageUri != ''")
    suspend fun getRecordsWithImage(): List<DietRecord>

    @Update
    suspend fun updateAll(records: List<DietRecord>)

    @Query("SELECT * FROM DietRecord ORDER BY timestamp DESC")
    fun pagingSource(): PagingSource<Int, DietRecord>

    @Query("SELECT * FROM DietRecord ORDER BY timestamp ASC")
    suspend fun getAllOnce(): List<DietRecord>

    /** 去重用轻量投影：只取 (date, timestamp, mealType)，避免全量物化 recognizedFoodJson 等大字段 */
    @Query("SELECT date, timestamp, mealType FROM DietRecord")
    suspend fun getDedupKeys(): List<DietRecordDedupKey>
}

/** 备份去重键：与 [com.example.weight.data.backup.BackupDeduplicator] 的 (date, timestamp, mealType) 口径一致 */
data class DietRecordDedupKey(val date: String, val timestamp: Long, val mealType: String)

data class TrafficLightCount(val trafficLight: String, val count: Int)

/** 单日摄入热量合计（AI 分析 Prompt 用投影） */
data class DailyCalories(val date: String, val calories: Int)
