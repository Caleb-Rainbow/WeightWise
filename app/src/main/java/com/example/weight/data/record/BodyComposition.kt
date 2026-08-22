package com.example.weight.data.record

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 体脂秤测得的身体成分快照，以 JSON 存进 [Record.bodyComposition]。
 * 字段 0/空表示未测得（旧记录 JSON 缺键反序列化为默认值，与 DietRecord 宏量字段同哲学）。
 * 率类单位 %，量类单位 kg，等级/得分为整数。
 */
@Immutable
@Serializable
data class BodyComposition(
    /** 体脂率 % */
    val fatRatio: Double = 0.0,
    /** 水分率 % */
    val waterRatio: Double = 0.0,
    /** 肌肉率 %（肌肉量/体重） */
    val muscleRatio: Double = 0.0,
    /** 生物电阻抗 Ω，秤上报的原始测量值 */
    val impedance: Int = 0,
    /** 去脂体重 kg（Fat-Free Mass，Sun 2003） */
    val ffm: Double = 0.0,
    /** 肌肉量 kg（去脂体重−骨量，商用秤口径） */
    val muscleMass: Double = 0.0,
    /** 骨量 kg */
    val boneMass: Double = 0.0,
    /** 骨骼肌量 kg（Skeletal Muscle Mass，Janssen 2000） */
    val skeletalMuscleMass: Double = 0.0,
    /** 骨骼肌率 % */
    val skeletalMuscleRatio: Double = 0.0,
    /** 蛋白质率 % */
    val proteinRatio: Double = 0.0,
    /** 皮下脂肪率 % */
    val subcutaneousFatRatio: Double = 0.0,
    /** 内脏脂肪等级（1-20，商用口径） */
    val visceralFatLevel: Int = 0,
    /** 体型判定，如「标准型」「虚胖型」 */
    val bodyType: String = "",
    /** 身体得分 1-100 */
    val bodyScore: Int = 0,
) {
    val hasAny: Boolean get() = fatRatio > 0 || waterRatio > 0 || muscleRatio > 0 || impedance > 0

    /**
     * 面向展示的指标项列表（标签 to 显示值），按重要性排序：结论项（体型/得分）→
     * 脂肪类 → 水分肌肉类 → 基础量 → 原始阻抗；未测得的项（0/空）自动跳过，
     * 因此手动记录（无成分）与回退路径（仅体脂）都能安全复用同一网格 UI。
     */
    fun metricItems(): List<Pair<String, String>> = buildList {
        if (bodyType.isNotBlank()) add("体型" to bodyType)
        if (bodyScore > 0) add("身体得分" to "$bodyScore")
        if (fatRatio > 0) add("体脂率" to "${fmt(fatRatio)}%")
        if (visceralFatLevel > 0) add("内脏脂肪" to "等级 $visceralFatLevel")
        if (subcutaneousFatRatio > 0) add("皮下脂肪率" to "${fmt(subcutaneousFatRatio)}%")
        if (waterRatio > 0) add("水分率" to "${fmt(waterRatio)}%")
        if (muscleMass > 0) add("肌肉量" to "${fmt(muscleMass)} kg")
        if (skeletalMuscleMass > 0) add("骨骼肌量" to "${fmt(skeletalMuscleMass)} kg")
        if (skeletalMuscleRatio > 0) add("骨骼肌率" to "${fmt(skeletalMuscleRatio)}%")
        if (proteinRatio > 0) add("蛋白质率" to "${fmt(proteinRatio)}%")
        if (ffm > 0) add("去脂体重" to "${fmt(ffm)} kg")
        if (boneMass > 0) add("骨量" to "${fmt(boneMass)} kg")
        if (impedance > 0) add("阻抗" to "$impedance Ω")
    }

    private fun fmt(v: Double): String =
        if (v == v.toLong().toDouble()) v.toLong().toString() else String.format("%.1f", v)
}

/** Record.bodyComposition 列的编解码；坏数据返回 null 而不是抛异常 */
object BodyCompositionJson {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(c: BodyComposition): String = json.encodeToString(c)

    fun decode(raw: String?): BodyComposition? {
        if (raw.isNullOrBlank()) return null
        return runCatching { json.decodeFromString<BodyComposition>(raw) }.getOrNull()
    }
}
