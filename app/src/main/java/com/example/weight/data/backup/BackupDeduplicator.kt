package com.example.weight.data.backup

import com.example.weight.data.diet.DietRecord
import com.example.weight.data.diet.DietRecordDedupKey
import com.example.weight.data.record.Record
import com.example.weight.data.record.RecordDedupKey

/**
 * 导入去重：已存在的记录跳过，只返回需要新增的部分。纯函数便于单测。
 * 体重记录按 (timestamp, weight) 去重；饮食记录按 (date, timestamp, mealType) 去重。
 * 现有数据以去重键投影传入（见 DAO 的 getDedupKeys），避免为去重全量物化大字段。
 */
object BackupDeduplicator {

    data class DedupResult<T>(val toInsert: List<T>, val skippedCount: Int)

    fun filterNewRecords(existingKeys: List<RecordDedupKey>, incoming: List<RecordBackup>): DedupResult<Record> {
        val existing = existingKeys.mapTo(HashSet(existingKeys.size)) { RecordKey(it.timestamp, it.weight) }
        val toInsert = ArrayList<Record>(incoming.size)
        var skipped = 0
        for (item in incoming) {
            // 非法数据防御：体重或时间戳无效的行直接丢弃
            if (item.weight <= 0.0 || item.timestamp <= 0L) {
                skipped++
                continue
            }
            if (RecordKey(item.timestamp, item.weight) in existing) {
                skipped++
            } else {
                toInsert.add(Record(weight = item.weight, log = item.log, timestamp = item.timestamp))
            }
        }
        return DedupResult(toInsert, skipped)
    }

    fun filterNewDietRecords(
        existingKeys: List<DietRecordDedupKey>,
        incoming: List<DietRecordBackup>,
        buildImageUri: (fileName: String) -> String,
    ): DedupResult<DietRecord> {
        val existing = existingKeys.mapTo(HashSet(existingKeys.size)) { DietKey(it.date, it.timestamp, it.mealType) }
        val toInsert = ArrayList<DietRecord>(incoming.size)
        var skipped = 0
        for (item in incoming) {
            if (item.timestamp <= 0L || item.mealType.isBlank()) {
                skipped++
                continue
            }
            if (DietKey(item.date, item.timestamp, item.mealType) in existing) {
                skipped++
            } else {
                toInsert.add(
                    DietRecord(
                        date = item.date,
                        timestamp = item.timestamp,
                        mealType = item.mealType,
                        imageUri = buildImageUri(item.imageFileName),
                        userInput = item.userInput,
                        recognizedFoodJson = item.recognizedFoodJson,
                        estimatedCalories = item.estimatedCalories,
                        trafficLight = item.trafficLight,
                    )
                )
            }
        }
        return DedupResult(toInsert, skipped)
    }

    /** 直接以字段为键，避免拼字符串的额外分配 */
    private data class RecordKey(val timestamp: Long, val weight: Double)

    private data class DietKey(val date: String, val timestamp: Long, val mealType: String)
}
