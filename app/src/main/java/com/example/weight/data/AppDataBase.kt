package com.example.weight.data

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.DeleteTable
import androidx.room.RoomDatabase
import androidx.room.migration.AutoMigrationSpec
import com.example.weight.data.diet.DietRecord
import com.example.weight.data.diet.DietRecordDao
import com.example.weight.data.record.RecordDao
import com.example.weight.data.record.Record

/**
 * 7 -> 8：移除运动计划与减重旅程功能，删除对应的数据表。
 */
@DeleteTable(tableName = "DailyPlan")
@DeleteTable(tableName = "ExerciseCompletion")
@DeleteTable(tableName = "Journey")
@DeleteTable(tableName = "Phase")
class Migration7To8 : AutoMigrationSpec

/**
 * 8 -> 9：Record 补 timestamp 索引（时间窗查询/排序走索引），
 * DietRecord 的 mealType 索引（无查询使用）换成 timestamp 索引（分页排序用）。
 */
@Database(
    version = 9,
    entities = [Record::class, DietRecord::class],
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6),
        AutoMigration(from = 6, to = 7),
        AutoMigration(from = 7, to = 8, spec = Migration7To8::class),
        AutoMigration(from = 8, to = 9),
    ],
)
abstract class AppDataBase : RoomDatabase() {
    abstract fun recordDao(): RecordDao
    abstract fun dietRecordDao(): DietRecordDao
}
