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

    @Update
    suspend fun update(record: DietRecord)

    @Delete
    suspend fun delete(record: DietRecord)

    @Query("SELECT * FROM DietRecord WHERE date = :date ORDER BY timestamp ASC")
    fun getByDate(date: String): Flow<List<DietRecord>>

    @Query("SELECT * FROM DietRecord WHERE date = :date ORDER BY timestamp ASC")
    suspend fun getByDateOnce(date: String): List<DietRecord>

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

    @Query("SELECT * FROM DietRecord WHERE date >= :sinceDate ORDER BY date ASC, timestamp ASC")
    suspend fun getRecordsSince(sinceDate: String): List<DietRecord>

    @Query("SELECT * FROM DietRecord ORDER BY timestamp DESC")
    fun pagingSource(): PagingSource<Int, DietRecord>
}

data class TrafficLightCount(val trafficLight: String, val count: Int)
