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

    /** 历史 Tab 取数：[startDate] 含、[endDate] 排他（yyyy-MM-dd 字典序），按日期倒序+日内时间正序 */
    @Query(
        """
        SELECT * FROM DietRecord
        WHERE date >= :startDate AND date < :endDate
        ORDER BY date DESC, timestamp ASC
        """
    )
    fun getByDateRange(startDate: String, endDate: String): Flow<List<DietRecord>>

    /**
     * 常用食物聚合用轻量投影：只取 JSON 大字段且限定日期窗口（双端含），避免全表物化
     * （沿用 getDedupKeys 的惯例）。Flow 随表任意写操作重发，编辑/删除/撤销无需手动刷新；
     * today 上界挡未来日期补记提前参与统计（日期选择器允许选未来日期）
     */
    @Query("SELECT recognizedFoodJson FROM DietRecord WHERE date >= :sinceDate AND date <= :today AND recognizedFoodJson != ''")
    fun getFoodJsonBetween(sinceDate: String, today: String): Flow<List<String>>

    @Query("SELECT COALESCE(SUM(estimatedCalories), 0) FROM DietRecord WHERE date = :date")
    suspend fun getDailyCalories(date: String): Int

    @Query("SELECT COALESCE(SUM(estimatedCalories), 0) FROM DietRecord WHERE date = :date")
    fun getDailyCaloriesFlow(date: String): Flow<Int>

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

    /** Health Connect 只导出本地产生的饮食，外部来源记录不会回写形成回环 */
    @Query("SELECT * FROM DietRecord WHERE healthConnectOrigin = '' ORDER BY timestamp ASC")
    suspend fun getLocalRecordsForHealthConnect(): List<DietRecord>

    @Query("SELECT * FROM DietRecord WHERE healthConnectId = :recordId LIMIT 1")
    suspend fun getByHealthConnectId(recordId: String): DietRecord?

    /** 移除曾从测试构建导入的污染数据；仅按明确来源包名删除。 */
    @Query("DELETE FROM DietRecord WHERE healthConnectOrigin = :originPackage")
    suspend fun deleteByHealthConnectOrigin(originPackage: String): Int

    @Query(
        "SELECT * FROM DietRecord WHERE ABS(timestamp - :timestamp) <= :toleranceMillis " +
            "ORDER BY ABS(timestamp - :timestamp) ASC LIMIT 1"
    )
    suspend fun findNearest(timestamp: Long, toleranceMillis: Long): DietRecord?
}

/** 备份去重键：与 [com.example.weight.data.backup.BackupDeduplicator] 的 (date, timestamp, mealType) 口径一致 */
data class DietRecordDedupKey(val date: String, val timestamp: Long, val mealType: String)

data class TrafficLightCount(val trafficLight: String, val count: Int)

/** 单日摄入热量合计（AI 分析 Prompt 用投影） */
data class DailyCalories(val date: String, val calories: Int)
