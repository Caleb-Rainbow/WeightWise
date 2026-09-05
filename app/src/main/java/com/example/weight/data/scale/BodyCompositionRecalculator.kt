package com.example.weight.data.scale

import com.example.weight.data.LocalStorageData
import com.example.weight.data.record.BodyCompositionJson
import com.example.weight.data.record.Record
import com.example.weight.data.record.RecordDao
import com.example.weight.util.Gender

/**
 * 历史身体成分重算：把含原始阻抗的旧口径记录按当前公式（RFM 腰围×Sun 阻抗融合）
 * 与当前身体档案（性别/年龄/身高/腰围）重刷，供公式升级后用户主动触发，不做自动迁移。
 *
 * 规则：只重算 JSON 里存有原始阻抗（impedance > 0）的本机记录——原始阻抗是重算的
 * 唯一输入，无阻抗记录（手动/自报/HC 合并）与 Health Connect 整条导入的外部来源
 * 记录跳过保持原样。写回沿用 [Record.create] 的 JSON+三列双写约定。
 *
 * 重算不可撤销（旧口径数值被覆盖），但原始阻抗保留在 JSON 中，公式再变仍可继续重算。
 */
class BodyCompositionRecalculator(private val recordDao: RecordDao) {

    data class Result(val recalculated: Int, val skipped: Int)

    /** 可重算条数，供按钮点击后预检（0 条时无需弹确认框） */
    suspend fun eligibleCount(): Int = recordDao.getAllOnce().count(::isEligible)

    /**
     * 全量重算；逐条写回（个人库量级毫秒级，调用方持 loading 遮罩）。
     * 档案取当前设置值：身高成年后不变，年龄按当前值近似。
     */
    suspend fun recalculate(): Result {
        val sexMale = Gender.entries.firstOrNull { it.name == LocalStorageData.gender.value } != Gender.FEMALE
        val age = LocalStorageData.age.value
        val heightCm = LocalStorageData.height.value.toInt()
        val waistCm = LocalStorageData.currentWaistCm.value.takeIf { it > 0 }

        var recalculated = 0
        var skipped = 0
        recordDao.getAllOnce().forEach { record ->
            val updated = if (isEligible(record)) {
                recalculateRecord(record, sexMale, age, heightCm, waistCm)
            } else {
                null
            }
            if (updated == null) {
                skipped++
            } else {
                recordDao.update(updated)
                recalculated++
            }
        }
        return Result(recalculated, skipped)
    }

    companion object {

        /** 本机产生且 JSON 存有原始阻抗的记录才可重算；解析失败视为不可 */
        fun isEligible(record: Record): Boolean {
            if (record.healthConnectOrigin.isNotEmpty()) return false
            if (record.bodyComposition.isBlank()) return false
            val impedance = BodyCompositionJson.decode(record.bodyComposition)?.impedance ?: 0
            return impedance > 0
        }

        /**
         * 单条重算纯函数：按记录当时的体重与存档阻抗、当前档案走
         * [BodyFatCalculator.resolve]，返回双写更新的新 [Record]；
         * 不可重算（无阻抗/公式失效）返回 null 保持原样。
         */
        fun recalculateRecord(
            record: Record,
            sexMale: Boolean,
            age: Int,
            heightCm: Int,
            waistCm: Double?,
        ): Record? {
            if (record.healthConnectOrigin.isNotEmpty()) return null
            val old = BodyCompositionJson.decode(record.bodyComposition) ?: return null
            if (old.impedance <= 0) return null
            val composition = BodyFatCalculator.resolve(
                sexMale = sexMale,
                age = age,
                heightCm = heightCm,
                weightKg = record.weight,
                impedanceOhm = old.impedance.toDouble(),
                scaleFatRatio = null,
                waistCm = waistCm,
            )?.copy(impedance = old.impedance) ?: return null
            return record.copy(
                bodyComposition = BodyCompositionJson.encode(composition),
                fatRatio = composition.fatRatio,
                muscleRatio = composition.muscleRatio,
                waterRatio = composition.waterRatio,
            )
        }
    }
}
