package com.example.weight.data.scale

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 期望值全部按文献系数手工推导（Sun 2003 / Janssen 2000 / RFM Woolcott 2018 /
 * Deurenberg 1991 + 生理常数分解）。
 * 锚点用例 bodyfat_101kg_male 对应本机真机实测（185cm/101.9kg/536Ω/24 岁/腰围 100cm）。
 */
class BodyFatCalculatorTest {

    // ---- 全指标锚点：真机实测案例 ----

    @Test
    fun `全指标_男185cm101kg536Ω24岁`() {
        val c = BodyFatCalculator.calculate(
            sexMale = true, age = 24, heightCm = 185, weightKg = 101.9, impedanceOhm = 536.0,
        )!!
        // H²/R = 34225/536 = 63.85
        // FFM = −10.68 + 0.65×63.85 + 0.26×101.9 = 57.3
        assertEquals(57.3, c.ffm, 0.1)
        // fat = (101.9−57.3)/101.9 = 43.8%
        assertEquals(43.8, c.fatRatio, 0.1)
        // TBW = 0.732×57.3 = 41.96 → 41.2%
        assertEquals(41.2, c.waterRatio, 0.15)
        // 骨量 = 57.3×0.055 = 3.2
        assertEquals(3.2, c.boneMass, 0.1)
        // 肌肉量 = 57.3−3.2 = 54.2 → 53.2%
        assertEquals(54.2, c.muscleMass, 0.1)
        assertEquals(53.2, c.muscleRatio, 0.15)
        // SMM = 0.401×63.85 + 3.825 − 0.071×24 + 5.102 = 32.8 → 32.2%
        assertEquals(32.8, c.skeletalMuscleMass, 0.1)
        assertEquals(32.2, c.skeletalMuscleRatio, 0.15)
        // 蛋白 = 0.85×(57.3−41.96−3.2) = 10.3 → 10.1%
        assertEquals(10.1, c.proteinRatio, 0.15)
        // BMI 29.8 → VFL = 0.12×43.8+0.22×29.8+0.1×24−7.0 = 7
        assertEquals(7, c.visceralFatLevel)
        // 皮下 = 43.8 − 7×0.3 = 41.7
        assertEquals(41.7, c.subcutaneousFatRatio, 0.15)
        // 体脂高 + 骨骼肌率低 → 虚胖型
        assertEquals("虚胖型", c.bodyType)
        // 100 − (43.8−20)×0.8 − 15 − 8 = 58
        assertEquals(58, c.bodyScore)
    }

    // ---- 体型九宫格 ----

    @Test
    fun `体型判定九宫格`() {
        // 男 标准脂 标准肌
        assertEquals("标准型", BodyTypeGrid.judge(true, 15.0, 45.0))
        // 男 低脂 高肌
        assertEquals("肌肉型", BodyTypeGrid.judge(true, 8.0, 55.0))
        // 男 高脂 低肌
        assertEquals("虚胖型", BodyTypeGrid.judge(true, 30.0, 30.0))
        // 男 高脂 高肌
        assertEquals("结实肥胖型", BodyTypeGrid.judge(true, 25.0, 55.0))
        // 男 高脂 标准肌
        assertEquals("肥胖型", BodyTypeGrid.judge(true, 25.0, 45.0))
        // 男 低脂 低肌
        assertEquals("瘦弱型", BodyTypeGrid.judge(true, 8.0, 30.0))
        // 男 标准脂 低肌
        assertEquals("苗条型", BodyTypeGrid.judge(true, 15.0, 30.0))
        // 男 标准脂 高肌
        assertEquals("健美型", BodyTypeGrid.judge(true, 15.0, 55.0))
        // 女 阈值平移：28% 仍标准带（男口径会是高）
        assertEquals("标准型", BodyTypeGrid.judge(false, 26.0, 35.0))
        assertEquals("肥胖型", BodyTypeGrid.judge(false, 32.0, 35.0))
    }

    // ---- 双路融合（RFM 腰围法 × Sun 阻抗法）----

    @Test
    fun `融合锚点_男185cm101kg536Ω24岁腰100`() {
        val c = BodyFatCalculator.calculate(
            sexMale = true, age = 24, heightCm = 185, weightKg = 101.9,
            impedanceOhm = 536.0, waistCm = 100.0,
        )!!
        // RFM = 64 − 20×1.85 = 27.0；Sun fat = 43.75
        // 融合 = 0.65×27.0 + 0.35×43.75 = 32.9
        assertEquals(32.9, c.fatRatio, 0.1)
        // FFM = 101.9×(1−0.329) = 68.4
        assertEquals(68.4, c.ffm, 0.1)
        // TBW = 0.732×68.4 = 50.07 → 49.1%
        assertEquals(49.1, c.waterRatio, 0.15)
        // 骨 = 68.41×0.055 = 3.8；肌肉量 = 68.41−3.76 = 64.65 → 64.7（恰过舍入边界）
        assertEquals(3.8, c.boneMass, 0.1)
        assertEquals(64.7, c.muscleMass, 0.1)
        assertEquals(63.4, c.muscleRatio, 0.15)
        // SMM 仍按 Janssen 阻抗方程，不受融合影响
        assertEquals(32.8, c.skeletalMuscleMass, 0.1)
        assertEquals(32.2, c.skeletalMuscleRatio, 0.15)
        // 蛋白 = 0.85×(68.4−50.07−3.8) = 12.4 → 12.2%
        assertEquals(12.2, c.proteinRatio, 0.15)
        // BMI 29.8 → VFL = 0.12×32.9+0.22×29.8+2.4−7 = 6
        assertEquals(6, c.visceralFatLevel)
        // 皮下 = 32.9 − 6×0.3 = 31.1
        assertEquals(31.1, c.subcutaneousFatRatio, 0.15)
        // 体脂仍高带 + 骨骼肌率低带 → 虚胖型
        assertEquals("虚胖型", c.bodyType)
        // 100 − (32.9−20)×0.8 − 15 − 8 = 67
        assertEquals(67, c.bodyScore)
        assertEquals("fused_rfm_sun", c.fatMethod)
    }

    @Test
    fun `融合_无腰围退纯Sun`() {
        val c = BodyFatCalculator.calculate(true, 24, 185, 101.9, 536.0)!!
        assertEquals(43.8, c.fatRatio, 0.1)
        assertEquals("sun2003", c.fatMethod)
    }

    @Test
    fun `融合_腰围越界视为未设置`() {
        // 30cm / 250cm 均在 50-200 合理域之外 → 退纯 Sun
        assertEquals(43.8, BodyFatCalculator.calculate(true, 24, 185, 101.9, 536.0, waistCm = 30.0)!!.fatRatio, 0.1)
        assertEquals(43.8, BodyFatCalculator.calculate(true, 24, 185, 101.9, 536.0, waistCm = 250.0)!!.fatRatio, 0.1)
    }

    @Test
    fun `融合_女性`() {
        // RFM = 76 − 20×(165/75) = 32.0；H²/R = 27225/500 = 54.45
        // Sun FFM = −9.53+0.69×54.45+0.17×60 = 38.24 → fat 36.3
        // 融合 = 0.65×32.0+0.35×36.3 = 33.5
        val c = BodyFatCalculator.calculate(false, 30, 165, 60.0, 500.0, waistCm = 75.0)!!
        assertEquals(33.5, c.fatRatio, 0.1)
        assertEquals(39.9, c.ffm, 0.1)
        assertEquals("fused_rfm_sun", c.fatMethod)
    }

    @Test
    fun `RFM单独路径_无阻抗有腰围`() {
        // fat = 64 − 20×1.85 = 27.0；FFM = 101.9×0.73 = 74.4；阻抗派生指标为 0
        val c = BodyFatCalculator.resolve(true, 24, 185, 101.9, null, scaleFatRatio = null, waistCm = 100.0)!!
        assertEquals(27.0, c.fatRatio, 0.1)
        assertEquals(74.4, c.ffm, 0.1)
        assertEquals(0.0, c.skeletalMuscleMass, 0.001)
        assertEquals(0, c.impedance)
        assertEquals("rfm", c.fatMethod)
        // fat 27 高带 + SMM 未知 → 肥胖型
        assertEquals("肥胖型", c.bodyType)
    }

    @Test
    fun `口径存档_自报与回退路径`() {
        assertEquals(
            "scale_reported",
            BodyFatCalculator.resolve(true, 24, 185, 101.9, null, scaleFatRatio = 43.8)!!.fatMethod,
        )
        // 有腰围时 RFM 优先于 Deurenberg（腰围比 BMI 法更准）
        assertEquals(
            "rfm",
            BodyFatCalculator.resolve(true, 30, 170, 70.0, null, scaleFatRatio = null, waistCm = 90.0)!!.fatMethod,
        )
        assertEquals(
            "deurenberg",
            BodyFatCalculator.resolve(true, 30, 170, 70.0, null, scaleFatRatio = null)!!.fatMethod,
        )
    }

    // ---- resolve 统一入口：阻抗 / 秤自报体脂 / 双缺失回退 ----


    @Test
    fun `resolve_阻抗路径与calculate等价`() {
        val a = BodyFatCalculator.resolve(true, 24, 185, 101.9, 536.0, scaleFatRatio = null)!!
        val b = BodyFatCalculator.calculate(true, 24, 185, 101.9, 536.0)!!
        assertEquals(a, b)
    }

    @Test
    fun `resolve_无阻抗走秤自报体脂质量平衡`() {
        // FFM=57.268 与阻抗锚点几乎同值；肌肉量 = 57.268×0.945 = 54.12 → 54.1（阻抗路径为 54.2）
        val c = BodyFatCalculator.resolve(true, 24, 185, 101.9, null, scaleFatRatio = 43.8)!!
        assertEquals(57.3, c.ffm, 0.1)
        assertEquals(43.8, c.fatRatio, 0.1)
        assertEquals(41.2, c.waterRatio, 0.15)
        assertEquals(54.1, c.muscleMass, 0.05)
        assertEquals(0.0, c.skeletalMuscleMass, 0.001)
        assertEquals(0.0, c.skeletalMuscleRatio, 0.001)
        assertEquals(0, c.impedance)
        // 无阻抗 → SMM 未测得 → 九宫格肌肉维度按"未知不误判"回退：高脂+未知肌 = 肥胖型（非虚胖型）
        assertEquals("肥胖型", c.bodyType)
        // 体脂偏离 23.8×0.8=19.04，水分 41.2 偏离带扣 8，SMM/内脏不扣 → 73
        assertEquals(73, c.bodyScore)
    }

    @Test
    fun `resolve_自报体脂越界忽略走Deurenberg回退`() {
        val c = BodyFatCalculator.resolve(true, 30, 170, 70.0, null, scaleFatRatio = 99.0)!!
        // BMI = 24.2 → Deurenberg 男 30 岁 = 19.8（与阻抗越界回退用例同值）
        assertEquals(19.8, c.fatRatio, 0.1)
        assertEquals(0.0, c.ffm, 0.001)
    }

    @Test
    fun `resolve_双缺失且Deurenberg域外返回null`() {
        // BMI = 12.5 < 13 下限 → 回退也出不来
        assertNull(BodyFatCalculator.resolve(true, 30, 170, 25.0, null, scaleFatRatio = null))
    }

    // ---- 回退与防线 ----

    @Test
    fun `阻抗越界回退Deurenberg仅三项`() {
        // 阻抗 50Ω 越界 → BMI 方程：BMI=24.2，男 30 岁 → 19.8；阻抗派生指标全 0
        val c = BodyFatCalculator.calculate(true, 30, 170, 70.0, 50.0)!!
        assertEquals(19.8, c.fatRatio, 0.1)
        assertEquals(0.0, c.ffm, 0.001)
        assertEquals(0.0, c.skeletalMuscleMass, 0.001)
        assertEquals(0, c.visceralFatLevel)
        assertEquals("标准型", c.bodyType)
        assertTrue(c.bodyScore > 0)
    }

    @Test
    fun `FFM越界回退Deurenberg`() {
        // 女性 200cm 60kg 100Ω：FFM = −9.53+0.69×400+10.2 = 272 > 60 → 回退
        val c = BodyFatCalculator.calculate(false, 30, 200, 60.0, 100.0)!!
        // Deurenberg：BMI = 15 → 1.2×15+6.9−5.4 = 19.5
        assertEquals(19.5, c.fatRatio, 0.1)
    }

    @Test
    fun `非法体重身高返回null`() {
        assertNull(BodyFatCalculator.calculate(true, 30, 0, 60.0, 500.0))
        assertNull(BodyFatCalculator.calculate(true, 30, 170, 0.0, 500.0))
    }

    @Test
    fun `体脂率夹在生理区间`() {
        val c = BodyFatCalculator.calculate(true, 60, 160, 140.0, 500.0)!!
        assertTrue(c.fatRatio <= 60.0)
        val lean = BodyFatCalculator.calculate(false, 20, 190, 48.0, 500.0)!!
        assertTrue(lean.fatRatio >= 3.0)
    }

    @Test
    fun `正常体型得分高于肥胖体型`() {
        // 175cm/70kg 正常男性典型阻抗 ~400Ω → 体脂 18% 标准带
        val normal = BodyFatCalculator.calculate(true, 30, 175, 70.0, 400.0)!!
        val obese = BodyFatCalculator.calculate(true, 24, 185, 101.9, 536.0)!!
        assertTrue(normal.bodyType == "标准型" || normal.bodyType == "健美型")
        assertTrue(normal.bodyScore > obese.bodyScore)
    }
}
