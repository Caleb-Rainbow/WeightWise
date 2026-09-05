package com.example.weight.data.scale

import com.example.weight.data.record.BodyComposition
import com.example.weight.data.record.BodyCompositionJson
import com.example.weight.data.record.Record
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 重算纯函数用例；锚点沿用真机实测档案（185cm/24 岁/腰围 100cm/101.9kg/536Ω），
 * 融合期望值与 BodyFatCalculatorTest 的融合锚点一致。
 */
class BodyCompositionRecalculatorTest {

    private fun oldRecord(
        weight: Double = 101.9,
        composition: BodyComposition? = BodyComposition(
            fatRatio = 43.8, waterRatio = 41.2, muscleRatio = 53.2,
            impedance = 536, ffm = 57.3,
        ),
        healthConnectOrigin: String = "",
    ) = Record(
        weight = weight,
        log = "",
        timestamp = 1_700_000_000_000,
        bodyComposition = composition?.let { BodyCompositionJson.encode(it) } ?: "",
        fatRatio = composition?.fatRatio ?: 0.0,
        muscleRatio = composition?.muscleRatio ?: 0.0,
        waterRatio = composition?.waterRatio ?: 0.0,
        healthConnectOrigin = healthConnectOrigin,
    )

    @Test
    fun `重算_含阻抗记录升级为融合口径`() {
        val updated = BodyCompositionRecalculator.recalculateRecord(
            oldRecord(), sexMale = true, age = 24, heightCm = 185, waistCm = 100.0,
        )
        assertNotNull(updated)
        // 融合锚点：fat 32.9 / FFM 68.4 / 水分 49.1（见 BodyFatCalculatorTest）
        val c = BodyCompositionJson.decode(updated!!.bodyComposition)!!
        assertEquals(32.9, c.fatRatio, 0.1)
        assertEquals(49.1, c.waterRatio, 0.15)
        assertEquals("fused_rfm_sun", c.fatMethod)
        // 原始阻抗保留在 JSON
        assertEquals(536, c.impedance)
        // 三冗余列与 JSON 同步双写
        assertEquals(c.fatRatio, updated.fatRatio, 0.001)
        assertEquals(c.muscleRatio, updated.muscleRatio, 0.001)
        assertEquals(c.waterRatio, updated.waterRatio, 0.001)
        // 体重/时间戳/日志不动
        assertEquals(101.9, updated.weight, 0.001)
        assertEquals(1_700_000_000_000, updated.timestamp)
    }

    @Test
    fun `重算_无腰围退纯Sun且数值不变`() {
        val updated = BodyCompositionRecalculator.recalculateRecord(
            oldRecord(), sexMale = true, age = 24, heightCm = 185, waistCm = null,
        )
        val c = BodyCompositionJson.decode(updated!!.bodyComposition)!!
        assertEquals(43.8, c.fatRatio, 0.1)
        assertEquals("sun2003", c.fatMethod)
    }

    @Test
    fun `重算_无阻抗记录跳过`() {
        // 秤自报/手动/HC 合并路径的历史记录：impedance=0，重算无原始输入
        val record = oldRecord(
            composition = BodyComposition(fatRatio = 43.8, fatMethod = "scale_reported"),
        )
        assertNull(
            BodyCompositionRecalculator.recalculateRecord(record, true, 24, 185, 100.0),
        )
        assertFalse(BodyCompositionRecalculator.isEligible(record))
    }

    @Test
    fun `重算_HC外部来源整条导入记录跳过`() {
        val record = oldRecord(healthConnectOrigin = "com.external.scale")
        assertNull(
            BodyCompositionRecalculator.recalculateRecord(record, true, 24, 185, 100.0),
        )
        assertFalse(BodyCompositionRecalculator.isEligible(record))
    }

    @Test
    fun `重算_空JSON与坏JSON跳过`() {
        // bodyComposition 空串（纯手动体重记录）
        assertFalse(BodyCompositionRecalculator.isEligible(oldRecord(composition = null)))
        // 坏 JSON：eligible 按 decode 失败处理，单条重算返回 null
        val bad = oldRecord().copy(bodyComposition = "not-json{")
        assertFalse(BodyCompositionRecalculator.isEligible(bad))
        assertNull(BodyCompositionRecalculator.recalculateRecord(bad, true, 24, 185, 100.0))
    }
}
