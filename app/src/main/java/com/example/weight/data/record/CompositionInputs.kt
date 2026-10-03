package com.example.weight.data.record

import com.example.weight.util.TimeUtils
import kotlinx.serialization.Serializable

/** 原始输入随测量归档。未知接线/频率不能被当成论文中的全身电阻。 */
object BiaMethod {
    const val UNKNOWN = "unknown"
    const val FOOT_TO_FOOT = "foot_to_foot"
    const val WRIST_TO_ANKLE_50KHZ = "wrist_to_ankle_50khz"
}

@Serializable
data class CompositionInputs(
    val sexMale: Boolean? = null,
    val age: Int = 0,
    val heightCm: Int = 0,
    val weightKg: Double = 0.0,
    val measuredAt: Long = 0,
    val waistCm: Double? = null,
    val waistMeasuredAt: Long = 0,
    val impedanceOhm: Double? = null,
    val biaMethod: String = BiaMethod.UNKNOWN,
    val sourceId: String = "",
    val scaleFatRatio: Double? = null,
    val scaleWaterRatio: Double? = null,
    val scaleMuscleRatio: Double? = null,
    val scaleBoneMass: Double? = null,
) {
    val hasValidProfile: Boolean get() = sexMale != null && age in 10..100 && heightCm in 100..250
    val hasValidWeight: Boolean get() = weightKg.isFinite() && weightKg in 1.0..400.0

    /** 只使用本次称重当天测得的腰围，无时间戳的旧设置不能冒充当日测量。 */
    val currentWaist: Double? get() = waistCm?.takeIf {
        it.isFinite() && it in 40.0..200.0 && measuredAt > 0 && waistMeasuredAt > 0 &&
            waistMeasuredAt <= measuredAt &&
            TimeUtils.beijingDate(measuredAt) == TimeUtils.beijingDate(waistMeasuredAt)
    }
}
