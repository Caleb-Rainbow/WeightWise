package com.example.weight.data.scale

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 测试数据全部来自公开逆向资料中的真实抓包：
 * - openScale issue #1428（AJIDO，AC 27 帧）
 * - openScale MGBHandler 与 Dr Trust 505（8 字节流式帧）
 * - mazeasdamien/runstar-fg2431-ble 协议文档
 */
class IcomonFrameParserTest {

    private fun bytes(vararg ints: Int) = ByteArray(ints.size) { ints[it].toByte() }

    // ---- 变体 B：AC 27 体重流帧 ----

    @Test
    fun `ac27 实测帧 77_51kg 测量中`() {
        // openScale issue #1428 原始帧：raw=0x692EC6，标签26，克数 0x12EC6=77510 → 77.51kg
        val p = bytes(
            0xAC, 0x27, 0x00, 0x69, 0x2E, 0xC6,
            0x02, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01,
        )
        val m = IcomonFrameParser.parse(p)
        assertNotNull(m)
        assertEquals(77.51, m!!.weightKg, 0.001)
        assertFalse(m.isFinal)
    }

    @Test
    fun `ac27 稳定标志在字节2最高位`() {
        // 同一载荷仅类型字节置 0x80：应识别为稳定终值
        val p = bytes(
            0xAC, 0x27, 0x80, 0x69, 0x2E, 0xC6,
            0x02, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01,
        )
        val m = IcomonFrameParser.parse(p)
        assertNotNull(m)
        assertTrue(m!!.isFinal)
        assertEquals(77.51, m.weightKg, 0.001)
    }

    @Test
    fun `ac27 标签35固件同样可解`() {
        // raw 高 6 位 = 35 (0x8C0000)，克数 73700 → 73.70kg
        val raw = 0x8C0000 or 73700
        val p = bytes(
            0xAC, 0x27, 0x00,
            (raw shr 16) and 0xFF, (raw shr 8) and 0xFF, raw and 0xFF,
            0x02, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01,
        )
        val m = IcomonFrameParser.parse(p)
        assertNotNull(m)
        assertEquals(73.70, m!!.weightKg, 0.001)
    }

    @Test
    fun `ac27 未知标签拒绝`() {
        val p = bytes(
            0xAC, 0x27, 0x00, 0x12, 0x34, 0x56,
            0x02, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01,
        )
        assertNull(IcomonFrameParser.parse(p))
    }

    @Test
    fun `ac27 零重量拒绝`() {
        val p = bytes(
            0xAC, 0x27, 0x00, 0x68, 0x00, 0x00,
            0x02, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01,
        )
        assertNull(IcomonFrameParser.parse(p))
    }

    // ---- 变体 A2：8 字节流式帧 ----

    @Test
    fun `八字节流式稳定帧`() {
        // 7530 → 75.30kg，flag=0xCA 稳定
        val p = bytes(0xAC, 0x02, 0x1D, 0x6A, 0x00, 0x00, 0xCA, 0xA9)
        val m = IcomonFrameParser.parse(p)
        assertNotNull(m)
        assertTrue(m!!.isFinal)
        assertEquals(75.30, m.weightKg, 0.001)
    }

    @Test
    fun `八字节流式测量中帧`() {
        // 0x1D55 = 7509 → 75.09kg，flag=0xCE 测量中
        val p = bytes(0xAC, 0x02, 0x1D, 0x55, 0x00, 0x00, 0xCE, 0xCA)
        val m = IcomonFrameParser.parse(p)
        assertNotNull(m)
        assertFalse(m!!.isFinal)
        assertEquals(75.09, m.weightKg, 0.001)
    }

    @Test
    fun `八字节阻抗与配置回显帧不当作体重`() {
        // flag=0xCB 阻抗、flag=0xCC 配置回显
        assertNull(IcomonFrameParser.parse(bytes(0xAC, 0x02, 0xFD, 0x01, 0x01, 0xC4, 0xCB, 0x8D)))
        assertNull(IcomonFrameParser.parse(bytes(0xAC, 0x02, 0xFE, 0x06, 0x01, 0x00, 0xCC, 0xD1)))
    }

    @Test
    fun `八字节 b4b5 非零非体重帧`() {
        assertNull(IcomonFrameParser.parse(bytes(0xAC, 0x02, 0xFD, 0x00, 0x00, 0x01, 0xCE, 0xD1)))
    }

    // ---- 变体 A：20 字节复合帧 ----

    @Test
    fun `复合帧超界体重拒绝`() {
        // 字节12-13 = 0x0750 = 1872 → 187.2kg 在合法域内；改用 0x7FFF=3276.7kg 超界应拒
        val p = bytes(
            0xAC, 0x02, 0xFF, 0x00, 0x02, 0x21,
            0x1A, 0x07, 0x16, 0x0E, 0x23, 0x28, // 时间戳占位
            0x7F, 0xFF, // 3276.7kg，超出 400kg 合法域
            0x00, 0x2A, // BMI
            0x01, 0x4E, // 体脂
            0x00, 0x00,
        )
        assertNull(IcomonFrameParser.parse(p))
    }

    @Test
    fun `复合帧第一包合法体重`() {
        val p = bytes(
            0xAC, 0x03, 0xFF, 0x00, 0x02, 0x21,
            0x1A, 0x07, 0x16, 0x0E, 0x23, 0x28,
            0x01, 0xD6, // 47.0kg
            0x00, 0x2A,
            0x01, 0x4E, // 33.4%
            0x00, 0x00,
        )
        val m = IcomonFrameParser.parse(p)
        assertNotNull(m)
        assertTrue(m!!.isFinal)
        assertEquals(47.0, m.weightKg, 0.001)
        assertEquals(33.4, m.fatRatio!!, 0.001)
    }

    @Test
    fun `复合帧第二包成分小端`() {
        val p = bytes(
            0x01, 0x00,
            0x10, 0x01, // 肌肉 0x0110=272 → 27.2%
            0x05, 0x00, // BMR 占位
            0x28, 0x00, // 骨骼 40 → 4.0kg
            0x2C, 0x01, // 水分 300 → 30.0%
            0x1E, // 年龄 30
            0x0F, 0x00, // 蛋白质
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        )
        assertEquals(20, p.size)
        val m = IcomonFrameParser.parse(p)
        assertNotNull(m)
        assertEquals(27.2, m!!.muscleRatio!!, 0.001)
        assertEquals(4.0, m.boneKg!!, 0.001)
        assertEquals(30.0, m.waterRatio!!, 0.001)
    }

    // ---- 握手包构造 ----
    // 变体 A 的 FFB1 buildHandshake 已删除：引擎统一走 AC 27 档案命令，变体 A 秤自报成分直接入库。

    // ---- AC 27 档案命令构造 ----

    @Test
    fun `档案命令布局与校验和`() {
        val cmd = IcomonFrameParser.buildAc27ProfileCommand(
            sexMale = true, age = 43, heightCm = 172, refWeightKg = 101.4,
        )
        assertEquals(20, cmd.size)
        assertEquals(0xAC.toByte(), cmd[0])
        assertEquals(0x27.toByte(), cmd[1])
        assertEquals(0x01.toByte(), cmd[2])
        assertEquals(0x00.toByte(), cmd[3])
        assertEquals(0x01, cmd[4].toInt() and 0xFF) // 男
        assertEquals(172, cmd[5].toInt() and 0xFF)
        // 参考体重 101.4×100 = 10140 = 0x279C 大端
        assertEquals(0x27, cmd[6].toInt() and 0xFF)
        assertEquals(0x9C, cmd[7].toInt() and 0xFF)
        assertEquals(43, cmd[8].toInt() and 0xFF)
        assertEquals(0xD1.toByte(), cmd[18])
        // 校验 = (字节2..18 求和) & 0x1F
        var sum = 0
        for (i in 2..18) sum += cmd[i].toInt() and 0xFF
        assertEquals((sum and 0x1F).toByte(), cmd[19])
    }

    @Test
    fun `真实体重帧校验和公式验证`() {
        // 本机实采帧：AC 27 00 8C 1A 40 ... 0C D5 07，末字节 0x07 应等于 (sum b2..18)&0x1F
        val p = bytes(
            0xAC, 0x27, 0x00, 0x8C, 0x1A, 0x40,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x0C, 0xD5, 0x07,
        )
        var sum = 0
        for (i in 2..18) sum += p[i].toInt() and 0xFF
        assertEquals(0x07, sum and 0x1F)
    }

    // ---- AC 27 结果帧（阻抗） ----

    @Test
    fun `结果帧MY_SCALE口径阻抗17-18除以5532`() {
        // 字节3-5 体重 101.4kg(tag35)；字节17-18 = 0x0A00 = 2560 → 2560/5.532 ≈ 462.9Ω
        val raw = 0x8C0000 or 101400
        val p = bytes(
            0xAC, 0x27, 0x01,
            (raw shr 16) and 0xFF, (raw shr 8) and 0xFF, raw and 0xFF,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x0A, 0x00, 0x00,
        )
        val m = IcomonFrameParser.parse(p)
        assertNotNull(m)
        assertTrue(m!!.isResultFrame)
        assertEquals(101.4, m.weightKg, 0.001)
        assertEquals(2560 / 5.532, m.impedanceOhm!!, 0.01)
        // 命中的是备选偏移 17（序列号慎信区），供上层告警
        assertEquals(17, m.impedanceSourceOffset)
    }

    @Test
    fun `结果帧FG2431口径阻抗4-5原始欧姆`() {
        // 主体重位(3-5)放无效标签，备位(8-10)放 77.5kg；阻抗在 4-5 = 500Ω
        // 注意字节 4-5 同时是主体重位中段，主体重 raw 会因此 tag 非法走备位
        val alt = 0x8C0000 or 77500
        val p = bytes(
            0xAC, 0x27, 0x01, 0x00, 0x01, 0xF4, // 主位 raw=0x0001F4 tag=0 非法
            0x00, 0x00,
            (alt shr 16) and 0xFF, (alt shr 8) and 0xFF, alt and 0xFF, // 备位体重 77.5kg
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        )
        val m = IcomonFrameParser.parse(p)
        assertNotNull(m)
        assertEquals(77.5, m!!.weightKg, 0.001)
        assertEquals(500.0, m.impedanceOhm!!, 0.001)
        assertEquals(4, m.impedanceSourceOffset)
    }

    @Test
    fun `结果帧本机实采真帧纯阻抗无体重`() {
        // 本机 logcat 实采：AC 27 01 00 02 12 00...0C D6 17 —— 体重位全空，阻抗在字节4-5=0x0212=530Ω
        val p = bytes(
            0xAC, 0x27, 0x01, 0x00, 0x02, 0x12,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x0C, 0xD6, 0x17,
        )
        val m = IcomonFrameParser.parse(p)
        assertNotNull(m)
        assertTrue(m!!.isResultFrame)
        assertEquals(0.0, m.weightKg, 0.001)
        assertEquals(530.0, m.impedanceOhm!!, 0.001)
        assertEquals(4, m.impedanceSourceOffset)
    }

    @Test
    fun `结果帧阻抗全部超区间时为null仍算体重`() {
        // 主位(3-5)=77 50 E0 tag 非法且 c4=0x7750=30532 超区间；备位(10-12,type02)=0x8D2EBC → 77.5kg；
        // 17-18 置 0 使 c17 也超区间
        val p = bytes(
            0xAC, 0x27, 0x02, 0x77, 0x50, 0xE0,
            0x00, 0x00, 0x00, 0x00, 0x8D, 0x2E, 0xBC,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        )
        val m = IcomonFrameParser.parse(p)
        assertNotNull(m)
        assertEquals(77.5, m!!.weightKg, 0.001)
        assertNull(m.impedanceOhm)
        assertEquals(-1, m.impedanceSourceOffset)
    }

    // ---- 杂项 ----

    @Test
    fun `未知帧返回null不抛异常`() {
        assertNull(IcomonFrameParser.parse(ByteArray(0)))
        assertNull(IcomonFrameParser.parse(byteArrayOf(0x38, 0x07, 0x00, 0x41))) // ICWeightScaleV3 帧
        assertNull(IcomonFrameParser.parse(ByteArray(20) { 0x55 }))
    }
}
