package com.example.weight.data

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.DeleteTable
import androidx.room.RoomDatabase
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.weight.data.diet.DietRecord
import com.example.weight.data.diet.DietRecordDao
import com.example.weight.data.record.BodyCompositionJson
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
/**
 * 10 -> 11：Record 冗余三列（fatRatio/muscleRatio/waterRatio），历史值由 JSON 回填。
 * 手动迁移（首个先例）：AutoMigration 只能加列不能回填。
 * 回填走 Kotlin 逐行 decode 而非 SQLite json_extract——后者依赖 json1 扩展的编译开关，
 * 部分 ROM/测试运行时不带，迁移抛错会把用户锁死在旧版本；个人库量级（千行内）逐行毫秒级。
 */
val MIGRATION_10_11: Migration = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `Record` ADD COLUMN `fatRatio` REAL NOT NULL DEFAULT 0.0")
        db.execSQL("ALTER TABLE `Record` ADD COLUMN `muscleRatio` REAL NOT NULL DEFAULT 0.0")
        db.execSQL("ALTER TABLE `Record` ADD COLUMN `waterRatio` REAL NOT NULL DEFAULT 0.0")
        db.query("SELECT `id`, `bodyComposition` FROM `Record` WHERE `bodyComposition` != ''").use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                val composition = runCatching {
                    BodyCompositionJson.decode(cursor.getString(1))
                }.getOrNull() ?: continue
                if (composition.fatRatio > 0 || composition.muscleRatio > 0 || composition.waterRatio > 0) {
                    db.execSQL(
                        "UPDATE `Record` SET `fatRatio` = ?, `muscleRatio` = ?, `waterRatio` = ? WHERE `id` = ?",
                        arrayOf<Any>(composition.fatRatio, composition.muscleRatio, composition.waterRatio, id),
                    )
                }
            }
        }
    }
}

@Database(
    version = 12,
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
        AutoMigration(from = 9, to = 10),
        AutoMigration(from = 11, to = 12),
    ],
)
abstract class AppDataBase : RoomDatabase() {
    abstract fun recordDao(): RecordDao
    abstract fun dietRecordDao(): DietRecordDao
}
