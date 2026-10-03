package com.example.weight.data.scale

import com.example.weight.data.record.BodyCompositionJson
import com.example.weight.data.record.Record
import com.example.weight.data.record.RecordDao

/** 只重放测量时归档的输入；旧记录缺少档案时跳过，绝不拿当前腰围覆盖过去。 */
class BodyCompositionRecalculator(private val recordDao: RecordDao) {
    data class Result(val recalculated: Int, val skipped: Int)

    suspend fun eligibleCount(): Int = recordDao.getAllOnce().count(::isEligible)

    suspend fun recalculate(): Result {
        var recalculated = 0
        var skipped = 0
        recordDao.getAllOnce().forEach { original ->
            val updated = recalculateRecord(original)
            val c = updated?.let { BodyCompositionJson.decode(it.bodyComposition) }
            if (c != null && recordDao.updateCompositionIfUnchanged(original, c)) recalculated++
            else skipped++
        }
        return Result(recalculated, skipped)
    }

    companion object {
        fun isEligible(record: Record): Boolean = recalculateRecord(record) != null

        fun recalculateRecord(record: Record): Record? {
            val old = BodyCompositionJson.decode(record.bodyComposition) ?: return null
            val input = old.inputs ?: return null
            if (input.measuredAt <= 0 || input.weightKg != record.weight) return null
            val c = BodyFatCalculator.resolve(input) ?: return null
            return record.copy(
                bodyComposition = BodyCompositionJson.encode(c),
                fatRatio = c.fatRatio, muscleRatio = c.muscleRatio, waterRatio = c.waterRatio,
            )
        }
    }
}
