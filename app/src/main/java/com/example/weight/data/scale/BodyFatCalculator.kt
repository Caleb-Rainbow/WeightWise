package com.example.weight.data.scale

import com.example.weight.data.record.BiaMethod
import com.example.weight.data.record.BodyComposition
import com.example.weight.data.record.CompositionInputs

/**
 * 可追溯的身体成分估算，不把缺测当正常，也不生成无校准依据的健康评分。
 *
 * Sun 2003 Table 5 (doi:10.1093/ajcn/77.2.331) 的最后一项是 +0.02*R，不是电抗。
 * 该模型仅用于明确的仰卧手腕至脚踝 50 kHz 电阻，脚部/未知阻抗只存原始值。
 * RFM (doi:10.1038/s41598-018-29362-1) 与 BMI 方程各自输出独立估算，不作经验融合。
 * Deurenberg (doi:10.1079/BJN19910073) 的成人方程从 16 岁开始，儿童不套成人式。
 * 水分、肌肉及骨量只接收秤端已有结果；不从固定生理比例补造指标。
 */
object BodyFatCalculator {
    const val ALGORITHM_VERSION = 2

    fun calculate(
        sexMale: Boolean?, age: Int, heightCm: Int, weightKg: Double,
        impedanceOhm: Double, waistCm: Double? = null,
        biaMethod: String = BiaMethod.UNKNOWN,
        measuredAt: Long = 0, waistMeasuredAt: Long = 0,
    ): BodyComposition? = resolve(
        sexMale, age, heightCm, weightKg, impedanceOhm, null, waistCm,
        biaMethod = biaMethod, measuredAt = measuredAt, waistMeasuredAt = waistMeasuredAt,
    )

    fun resolve(
        sexMale: Boolean?, age: Int, heightCm: Int, weightKg: Double,
        impedanceOhm: Double?, scaleFatRatio: Double?, waistCm: Double? = null,
        biaMethod: String = BiaMethod.UNKNOWN,
        measuredAt: Long = 0, waistMeasuredAt: Long = 0, sourceId: String = "",
        scaleWaterRatio: Double? = null, scaleMuscleRatio: Double? = null,
        scaleBoneMass: Double? = null,
    ): BodyComposition? = resolve(CompositionInputs(
        sexMale, age, heightCm, weightKg, measuredAt, waistCm, waistMeasuredAt,
        impedanceOhm, biaMethod, sourceId, scaleFatRatio, scaleWaterRatio,
        scaleMuscleRatio, scaleBoneMass,
    ))

    fun resolve(input: CompositionInputs): BodyComposition? {
        if (!input.hasValidWeight || !input.hasValidProfile) return null
        val male = input.sexMale ?: return null
        val weight = input.weightKg
        val bmi = weight / (input.heightCm / 100.0).let { it * it }
        val resistance = input.impedanceOhm?.takeIf { it.isFinite() && it in 100.0..1500.0 }
        val scaleFat = validPercent(input.scaleFatRatio)
        val scaleWater = validPercent(input.scaleWaterRatio)
        val scaleMuscle = validPercent(input.scaleMuscleRatio)
        val scaleBone = input.scaleBoneMass?.takeIf { it.isFinite() && it > 0 && it < weight }
        var method = ""
        val fat = when {
            scaleFat != null -> { method = "scale_reported"; scaleFat }
            input.biaMethod == BiaMethod.WRIST_TO_ANKLE_50KHZ && resistance != null &&
                input.age in 18..80 && bmi in 18.5..29.999 -> {
                val ffm = sun2003Ffm(male, input.heightCm, weight, resistance)
                val result = validPercent(100.0 * (1.0 - ffm / weight))
                if (result != null) method = "sun2003"
                result
            }
            else -> null
        } ?: input.currentWaist?.takeIf { input.age in 20..69 }?.let { waist ->
            validPercent((if (male) 64.0 else 76.0) - 20.0 * input.heightCm / waist)
                ?.also { method = "rfm" }
        } ?: if (input.age in 16..83 && bmi in 13.9..40.9) {
            validPercent(1.20 * bmi + 0.23 * input.age - (if (male) 10.8 else 0.0) - 5.4)
                ?.also { method = "deurenberg" }
        } else null

        // 保留计算精度，仅展示时取一位小数；去脂体重与体脂率从同一未截断值派生。
        val ffm = fat?.let { weight * (1.0 - it / 100.0) } ?: 0.0
        val bone = scaleBone?.takeIf { ffm == 0.0 || it <= ffm } ?: 0.0
        val muscle = scaleMuscle?.takeIf { ffm == 0.0 || weight * it / 100.0 + bone <= ffm } ?: 0.0
        val water = scaleWater?.takeIf { ffm == 0.0 || weight * it / 100.0 <= ffm } ?: 0.0
        val composition = BodyComposition(
            fatRatio = fat ?: 0.0, ffm = ffm,
            waterRatio = water, muscleRatio = muscle,
            muscleMass = weight * muscle / 100.0, boneMass = bone,
            impedance = resistance?.toInt() ?: 0,
            fatMethod = method, algorithmVersion = ALGORITHM_VERSION,
            inputs = input.copy(
                waistCm = input.waistCm?.takeIf { it.isFinite() }, impedanceOhm = resistance,
                scaleFatRatio = scaleFat, scaleWaterRatio = scaleWater,
                scaleMuscleRatio = scaleMuscle, scaleBoneMass = scaleBone,
            ),
        )
        return composition.takeIf { it.hasAny }
    }

    private fun validPercent(value: Double?): Double? = value?.takeIf {
        it.isFinite() && it > 0.0 && it < 100.0
    }

    private fun sun2003Ffm(male: Boolean, heightCm: Int, weightKg: Double, resistance: Double): Double {
        val index = heightCm.toDouble() * heightCm / resistance
        return if (male) -10.68 + 0.65 * index + 0.26 * weightKg + 0.02 * resistance
        else -9.53 + 0.69 * index + 0.17 * weightKg + 0.02 * resistance
    }
}
