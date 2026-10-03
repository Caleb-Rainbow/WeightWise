package com.example.weight.data.record

import androidx.compose.runtime.Immutable
import java.util.Locale
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 身体成分及其原始输入快照，以 JSON 存进 [Record.bodyComposition]。
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
    /** 去脂体重 kg，与体脂率和测量体重保持质量平衡 */
    val ffm: Double = 0.0,
    /** 秤端肌肉量 kg，不用去脂体重减骨量冒充肌肉 */
    val muscleMass: Double = 0.0,
    /** 骨量 kg */
    val boneMass: Double = 0.0,
    /** 旧版推导字段，仅保留存档兼容，不再生成或展示 */
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
    /** 体脂来源：sun2003 / scale_reported / rfm / deurenberg；fused_rfm_sun 仅见旧记录。 */
    val fatMethod: String = "",
    /** 0 是旧版；计算策略改变时增加版本，趋势不得跨版本比较。 */
    val algorithmVersion: Int = 0,
    /** 测量时原始输入，缺失时禁止拿当前档案重算历史。 */
    val inputs: CompositionInputs? = null,
) {
    val hasAny: Boolean get() = listOf(fatRatio, waterRatio, muscleRatio, muscleMass, boneMass, ffm)
        .any { it.isFinite() && it > 0 } || impedance > 0

    val sourceDescription: String get() = when {
        algorithmVersion < 2 && fatMethod in setOf("sun2003", "fused_rfm_sun") ->
            "旧版阻抗估算已停用，原始测量保留；缺少当时档案的记录无法准确重算"
        algorithmVersion < 2 -> "历史测量来源或档案不完整，未验证成分已停用；阻抗仅为存档值"
        fatMethod == "scale_reported" -> "体脂由秤端估算；其他成分仅展示秤端已提供的结果"
        fatMethod == "rfm" -> "体脂由当日腰围估算；去脂体重由体脂率推得"
        fatMethod == "deurenberg" -> "体脂由 BMI、年龄及性别估算；去脂体重由体脂率推得"
        fatMethod == "sun2003" -> "体脂由手腕至脚踝 50 kHz 电阻估算，尚无个人校准精度保证"
        else -> "秤端成分及原始测量；缺少适用的体脂估算输入"
    }

    /** 原始旧 JSON 不改写，展示时屏蔽已知错误的公式及无依据的推导结论。 */
    fun displayComposition(): BodyComposition {
        val verifiedInputs = algorithmVersion >= 2 && inputs?.hasValidProfile == true && inputs.hasValidWeight
        val legacyEstimate = !verifiedInputs
        val invalidFat = !verifiedInputs && fatMethod !in setOf("rfm", "deurenberg", "scale_reported")
        fun percent(v: Double) = v.takeIf { it.isFinite() && it > 0 && it < 100 } ?: 0.0
        fun mass(v: Double) = v.takeIf { it.isFinite() && it > 0 } ?: 0.0
        return copy(
            fatRatio = if (invalidFat) 0.0 else percent(fatRatio),
            ffm = if (legacyEstimate) 0.0 else mass(ffm),
            waterRatio = if (legacyEstimate) 0.0 else percent(waterRatio),
            muscleRatio = if (legacyEstimate) 0.0 else percent(muscleRatio),
            muscleMass = if (legacyEstimate) 0.0 else mass(muscleMass),
            boneMass = if (legacyEstimate) 0.0 else mass(boneMass),
            // 旧版这些字段均没有可验证的独立测量依据，保留存档，不再展示或判健康。
            skeletalMuscleMass = 0.0, skeletalMuscleRatio = 0.0,
            proteinRatio = 0.0, visceralFatLevel = 0, subcutaneousFatRatio = 0.0,
            bodyType = "", bodyScore = 0,
            impedance = impedance.takeIf { it in 100..1500 } ?: 0,
        )
    }

    /**
     * 面向展示的有效指标项。[key] 供指标解读弹窗（[MetricGuide]）反查定义；
     * 未测得的项（0/空）自动跳过，手动记录与回退路径都能安全复用同一网格 UI。
     */
    fun metricItems(): List<MetricDisplay> = buildList {
        val c = displayComposition()
        if (c.fatRatio > 0) add(MetricDisplay("fatRatio", "体脂率（估算）", "${fmt(c.fatRatio)}%"))
        if (c.waterRatio > 0) add(MetricDisplay("waterRatio", "秤端水分率", "${fmt(c.waterRatio)}%"))
        if (c.muscleMass > 0) add(MetricDisplay("muscleMass", "秤端肌肉量", "${fmt(c.muscleMass)} kg"))
        if (c.ffm > 0) add(MetricDisplay("ffm", "去脂体重（估算）", "${fmt(c.ffm)} kg"))
        if (c.boneMass > 0) add(MetricDisplay("boneMass", "秤端骨量", "${fmt(c.boneMass)} kg"))
        if (c.impedance > 0) add(MetricDisplay("impedance", if (algorithmVersion < 2) "存档阻抗" else "原始阻抗", "${c.impedance} Ω"))
    }

    private fun fmt(v: Double): String =
        if (v == v.toLong().toDouble()) v.toLong().toString() else String.format(Locale.CHINA, "%.1f", v)

    /** 按指标 key 反查原始数值（未测/非数值型返回 null），供 [MetricGuide] 状态判定 */
    fun rawValueOf(key: String): Double? {
        val c = displayComposition()
        return when (key) {
            "fatRatio" -> c.fatRatio
            "waterRatio" -> c.waterRatio
            "muscleMass" -> c.muscleMass
            "ffm" -> c.ffm
            "boneMass" -> c.boneMass
            "impedance" -> c.impedance.toDouble()
            else -> 0.0
        }.takeIf { it.isFinite() && it > 0 }
    }
}

/** 网格展示项：key 对应 [MetricGuide] 的指标定义 */
data class MetricDisplay(val key: String, val label: String, val value: String)

/** Record.bodyComposition 列的编解码；坏数据返回 null 而不是抛异常 */
object BodyCompositionJson {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(c: BodyComposition): String = json.encodeToString(c)

    fun decode(raw: String?): BodyComposition? {
        if (raw.isNullOrBlank()) return null
        return runCatching { json.decodeFromString<BodyComposition>(raw) }.getOrNull()
    }
}
