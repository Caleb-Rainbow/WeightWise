package com.example.weight.data.scale

import com.example.weight.data.record.BodyComposition
import kotlin.math.roundToInt

/**
 * 阻抗 + 身体档案 → 全套身体成分（对标商用体脂秤 App 的指标面板）。
 *
 * 家用脚部 BIA 秤只测阻抗，全部成分为 App 侧估算（精度 ±3-5%）。
 * 公式体系：文献回归方程（可引用、非 GPL）+ 生理常数分解 + 对标商用显示区间的启发式。
 *
 * ■ 基础方程（文献）
 * 1. 去脂体重 FFM(kg)：Sun 2003（NHANES III 四室模型）无电抗近似
 *    男 = −10.68 + 0.65×(H²/R) + 0.26×W；女 = −9.53 + 0.69×(H²/R) + 0.17×W
 *    （Am J Clin Nutr 2003;77(2):331-340，Xc 系数仅 0.02 去项偏差 <1.5kg）
 * 2. 体脂率 = (W − FFM)/W×100
 * 3. 总体水 TBW(kg) = 0.732×FFM（FFM 水合常数，Sun 2003 自用）；水分率 = TBW/W×100
 * 4. 骨骼肌量 SMM(kg)：Janssen 2000（MRI 验证）
 *    SMM = 0.401×(H²/R) + 3.825×sex(男1女0) − 0.071×age + 5.102
 *    （J Appl Physiol 2000;89(2):465-471）
 *
 * ■ 生理常数分解（推导量）
 * 5. 骨量 = clamp(FFM×0.055(男)/FFM×0.05(女), 1.5, 4.5)——骨矿物质约占 FFM 5-6%
 * 6. 肌肉量 = FFM − 骨量（水分/蛋白都在肌肉组织内，商用秤"肌肉量"口径）
 * 7. 蛋白质量 = 0.85×(FFM − TBW − 骨量)——干性无脂质量（FFM 的 26.8%）去骨矿后以蛋白为主
 *
 * ■ 启发式（对标商用秤显示区间，各家均为专有公式）
 * 8. 内脏脂肪等级 = clamp(round(0.12×fat% + 0.22×BMI + 0.10×age − 7.0(男)/9.0(女)), 1, 20)
 * 9. 皮下脂肪率 = max(fat% − 等级×0.3, fat%×0.6)——内脏脂肪折算后的皮下占比
 * 10. 体型判定：体脂带 × 骨骼肌率带 九宫格（见 [BodyTypeGrid]）
 * 11. 身体得分：100 起评，体脂偏离理想带 0.8/百分点(cap 40)、骨骼肌率低 −15、
 *     水分率偏离带 −8、内脏 >9 级 2/级(cap 15)，clamp 1..100
 *
 * 阻抗缺测/越界/FFM 异常时逐级回退：先试秤自报体脂率的质量平衡，再退 Deurenberg 1991
 * BMI 体脂方程
 * （fat% = 1.20×BMI + 0.23×age − 10.8×男 − 5.4，Br J Nutr 1991;65:105-114），
 * 回退路径下仅体脂率/体型/得分有值，阻抗派生指标为 0。
 */
object BodyFatCalculator {

    /**
     * 阻抗路径入口（AC 27 协议秤）；等价于 [resolve] 只带阻抗。
     * @return 成分估算；体重/身高非法（除零防线）时返回 null
     */
    fun calculate(
        sexMale: Boolean,
        age: Int,
        heightCm: Int,
        weightKg: Double,
        impedanceOhm: Double,
    ): BodyComposition? =
        resolve(sexMale, age, heightCm, weightKg, impedanceOhm, scaleFatRatio = null)

    /**
     * 统一入口：优先 Sun 2003 阻抗方程；无阻抗但秤自报了体脂率（变体 A 协议只上报算好的
     * 成分不出阻抗）时按质量平衡 FFM = W×(1−fat%) 补全派生指标，口径与阻抗路径一致；
     * 两者都不可用时回退 Deurenberg 仅体脂率/体型/得分。
     *
     * @param scaleFatRatio 秤自报体脂率 %；超出合理域视为脏数据忽略
     */
    fun resolve(
        sexMale: Boolean,
        age: Int,
        heightCm: Int,
        weightKg: Double,
        impedanceOhm: Double?,
        scaleFatRatio: Double?,
    ): BodyComposition? {
        if (weightKg <= 0 || heightCm <= 0) return null
        val heightM = heightCm / 100.0
        val bmi = weightKg / (heightM * heightM)

        if (impedanceOhm != null) {
            sun2003Ffm(sexMale, heightCm, weightKg, impedanceOhm)
                ?.let { return fullComposition(sexMale, age, heightCm, weightKg, bmi, it, impedanceOhm) }
        } else if (scaleFatRatio != null && scaleFatRatio in 3.0..60.0) {
            val ffm = weightKg * (1 - scaleFatRatio / 100.0)
            return fullComposition(sexMale, age, heightCm, weightKg, bmi, ffm, resistanceOhm = 0.0)
        }

        // 回退：Deurenberg 仅出体脂率/体型/得分
        val fat = deurenbergFat(sexMale, age, bmi) ?: return null
        val fatClamped = fat.coerceIn(3.0, 60.0)
        return BodyComposition(
            fatRatio = round1(fatClamped),
            bodyType = BodyTypeGrid.judge(sexMale, fatClamped, skeletalMuscleRatio = 0.0),
            bodyScore = bodyScore(sexMale, fatClamped, smmRatio = 0.0, waterRatio = 0.0, vfl = 0),
        )
    }

    /** 阻抗路径的全指标计算（[ffm] 已通过 (0, W) 校验） */
    private fun fullComposition(
        sexMale: Boolean,
        age: Int,
        heightCm: Int,
        weightKg: Double,
        bmi: Double,
        ffm: Double,
        resistanceOhm: Double,
    ): BodyComposition {
        val fat = ((weightKg - ffm) / weightKg * 100.0).coerceIn(3.0, 60.0)
        val tbw = 0.732 * ffm
        val waterRatio = (tbw / weightKg * 100.0).coerceIn(35.0, 65.0)

        val bone = (ffm * if (sexMale) 0.055 else 0.05).coerceIn(1.5, 4.5)
        val muscleMass = ffm - bone
        val smm = janssenSmm(sexMale, age, heightCm, resistanceOhm)
            .takeIf { resistanceOhm > 0 }
            ?.coerceIn(0.0, muscleMass)
            ?: 0.0
        val smmRatio = if (smm > 0) (smm / weightKg * 100.0).coerceIn(15.0, 60.0) else 0.0
        val proteinRatio = (0.85 * (ffm - tbw - bone) / weightKg * 100.0).coerceIn(5.0, 25.0)

        val vfl = (0.12 * fat + 0.22 * bmi + 0.10 * age - if (sexMale) 7.0 else 9.0)
            .roundToInt().coerceIn(1, 20)
        val subcutaneous = (fat - vfl * 0.3).coerceAtLeast(fat * 0.6)

        return BodyComposition(
            fatRatio = round1(fat),
            waterRatio = round1(waterRatio),
            muscleRatio = round1(muscleMass / weightKg * 100.0),
            ffm = round1(ffm),
            muscleMass = round1(muscleMass),
            boneMass = round1(bone),
            skeletalMuscleMass = round1(smm),
            skeletalMuscleRatio = round1(smmRatio),
            proteinRatio = round1(proteinRatio),
            subcutaneousFatRatio = round1(subcutaneous),
            visceralFatLevel = vfl,
            bodyType = BodyTypeGrid.judge(sexMale, fat, smmRatio),
            bodyScore = bodyScore(sexMale, fat, smmRatio, waterRatio, vfl),
        )
    }

    /** Sun 2003 FFM 方程（无电抗近似）；阻抗越界或 FFM 落在 (0, W) 之外时返回 null 走回退 */
    private fun sun2003Ffm(sexMale: Boolean, heightCm: Int, weightKg: Double, resistanceOhm: Double): Double? {
        if (resistanceOhm !in 100.0..1500.0) return null
        val h2OverR = heightCm.toDouble() * heightCm / resistanceOhm
        val ffm = if (sexMale) {
            -10.68 + 0.65 * h2OverR + 0.26 * weightKg
        } else {
            -9.53 + 0.69 * h2OverR + 0.17 * weightKg
        }
        if (ffm <= 0.0 || ffm >= weightKg) return null
        return ffm
    }

    /** Janssen 2000 骨骼肌量；调用方保证 resistanceOhm > 0（无阻抗时 SMM 记为未测得） */
    private fun janssenSmm(sexMale: Boolean, age: Int, heightCm: Int, resistanceOhm: Double): Double {
        val h2OverR = heightCm.toDouble() * heightCm / resistanceOhm
        return 0.401 * h2OverR + (if (sexMale) 3.825 else 0.0) - 0.071 * age + 5.102
    }

    /** Deurenberg 1991 BMI 方程；BMI/年龄超出拟合域时返回 null（文献拟合域 18-100 岁、BMI 到肥胖Ⅲ级） */
    private fun deurenbergFat(sexMale: Boolean, age: Int, bmi: Double): Double? {
        if (age !in 15..100 || bmi !in 13.0..60.0) return null
        val sexTerm = if (sexMale) 10.8 else 0.0
        return 1.20 * bmi + 0.23 * age - sexTerm - 5.4
    }

    /** 身体得分：透明扣分制，各项见类注释第 11 条 */
    private fun bodyScore(sexMale: Boolean, fat: Double, smmRatio: Double, waterRatio: Double, vfl: Int): Int {
        val idealFat = if (sexMale) 10.0..20.0 else 18.0..28.0
        val idealWater = if (sexMale) 50.0..65.0 else 45.0..60.0
        val idealSmm = if (sexMale) 38.0..50.0 else 30.0..40.0

        var score = 100.0
        val fatDeviation = maxOf(fat - idealFat.endInclusive, idealFat.start - fat, 0.0)
        score -= (fatDeviation * 0.8).coerceAtMost(40.0)
        if (smmRatio > 0 && smmRatio < idealSmm.start) score -= 15.0
        if (waterRatio > 0 && waterRatio !in idealWater) score -= 8.0
        if (vfl > 9) score -= ((vfl - 9) * 2.0).coerceAtMost(15.0)
        return score.roundToInt().coerceIn(1, 100)
    }

    private fun round1(v: Double) = kotlin.math.round(v * 10) / 10
}

/** 体型九宫格：体脂带（低/标准/高）× 骨骼肌率带（低/标准/高） */
object BodyTypeGrid {
    // 男：fat <10 / 10-20 / >20；女：<18 / 18-28 / >28
    // 男 SMM%：<38 / 38-50 / >50；女：<30 / 30-40 / >40
    private fun fatHigh(sexMale: Boolean, fat: Double) = fat > if (sexMale) 20.0 else 28.0
    private fun fatLow(sexMale: Boolean, fat: Double) = fat < if (sexMale) 10.0 else 18.0
    private fun smmHigh(sexMale: Boolean, smm: Double) = smm > if (sexMale) 50.0 else 40.0
    private fun smmLow(sexMale: Boolean, smm: Double) = smm in 1.0..if (sexMale) 38.0 else 30.0

    /** 骨骼肌率带判定：低带；回退路径（smmRatio=0 无数据）时按标准带处理避免误判 */
    fun judge(sexMale: Boolean, fat: Double, skeletalMuscleRatio: Double): String {
        val hasSmm = skeletalMuscleRatio > 0
        val lowMuscle = hasSmm && smmLow(sexMale, skeletalMuscleRatio)
        val highMuscle = hasSmm && smmHigh(sexMale, skeletalMuscleRatio)
        return when {
            fatHigh(sexMale, fat) && lowMuscle -> "虚胖型"
            fatHigh(sexMale, fat) && highMuscle -> "结实肥胖型"
            fatHigh(sexMale, fat) -> "肥胖型"
            fatLow(sexMale, fat) && lowMuscle -> "瘦弱型"
            fatLow(sexMale, fat) && highMuscle -> "肌肉型"
            fatLow(sexMale, fat) -> "纤瘦型"
            lowMuscle -> "苗条型"
            highMuscle -> "健美型"
            else -> "标准型"
        }
    }
}
