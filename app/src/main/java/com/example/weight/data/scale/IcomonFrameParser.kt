package com.example.weight.data.scale

/**
 * icomon（沃莱）家族体脂秤蓝牙帧解析器。
 *
 * 该厂商固件变体众多，同一 GATT 外形（服务 FFB0 / 写 FFB1 / 通知 FFB2）下至少有四种已知帧格式，
 * 全部在此按帧头/长度分派解析；无法识别的帧返回 null，由上层原样记录到调试日志。
 *
 * 参考实现：openScale MGBHandler.kt、runstar-fg2431-ble、lxgxdx/icomon_scale、genelee26/icomon_scale。
 */
object IcomonFrameParser {

    /** 一次称重的完整结果：体重必有，体脂率等成分字段视固件而定 */
    data class Measurement(
        val weightKg: Double,
        val isFinal: Boolean,
        val fatRatio: Double? = null,
        val muscleRatio: Double? = null,
        val boneKg: Double? = null,
        val waterRatio: Double? = null,
        /** 结果帧（AC 27 type 0x01/0x02）解析出的阻抗 Ω；体重流帧无此值 */
        val impedanceOhm: Double? = null,
        /**
         * 阻抗命中的候选字节偏移（4=本机实测主字段，6/17=其他固件口径候选，-1=未命中）。
         * 主字段以外的候选命中意味着协议假设可能不成立，由上层打日志留证。
         */
        val impedanceSourceOffset: Int = -1,
        /** true = 这是稳定后的 BIA 结果帧（携带阻抗），用于引擎区分普通稳定体重帧 */
        val isResultFrame: Boolean = false,
    )

    private const val TAG_26 = 26
    private const val TAG_35 = 35

    /** 成年人脚部 BIA 阻抗合理区间（Ω），超出视为该偏移不是阻抗字段 */
    private val IMPEDANCE_RANGE = 150.0..900.0

    /**
     * 解析一帧通知数据；不匹配任何已知格式时返回 null。
     *
     * @param payload FFB2 特征通知的原始字节
     */
    fun parse(payload: ByteArray): Measurement? {
        return when {
            payload.size == 8 && payload[0] == 0xAC.toByte() && payload[1] == 0x02.toByte() ->
                parseStreaming8(payload)
            payload.size == 20 && payload[0] == 0xAC.toByte() &&
                (payload[1] == 0x02.toByte() || payload[1] == 0x03.toByte()) &&
                payload[2] == 0xFF.toByte() -> parseCompositeFirst(payload)
            payload.size == 20 && payload[0] == 0x01.toByte() && payload[1] == 0x00.toByte() ->
                parseCompositeSecond(payload)
            payload.size == 20 && payload[0] == 0xAC.toByte() && payload[1] == 0x27.toByte() ->
                parseAc27(payload)
            else -> null
        }
    }

    /**
     * 变体 A2：8 字节流式帧 `AC 02 b2 b3 b4 b5 flag chk`。
     * flag=0xCA 稳定、0xCE 测量中；体重 = ((b2<<8)|b3)/100，仅当 b4==b5==0 时有效。
     */
    private fun parseStreaming8(p: ByteArray): Measurement? {
        val flag = p[6].toInt() and 0xFF
        val isFinal = when (flag) {
            0xCA -> true
            0xCE -> false
            else -> return null // 0xCB 阻抗 / 0xCC 配置回显等非体重帧
        }
        if (p[4].toInt() != 0 || p[5].toInt() != 0) return null
        val raw = ((p[2].toInt() and 0xFF) shl 8) or (p[3].toInt() and 0xFF)
        if (raw == 0) return null
        return Measurement(weightKg = raw / 100.0, isFinal = isFinal)
    }

    /**
     * 变体 A：20 字节复合帧第一包，头 `AC 02|03 FF`。
     * 字节 12-13 大端 u16 ×0.1 为体重(kg)，14-15 BMI，16-17 大端 u16 ×0.1 为体脂率(%)。
     */
    private fun parseCompositeFirst(p: ByteArray): Measurement? {
        val weight = u16be(p, 12) / 10.0
        if (weight <= 0 || weight > 400) return null
        val fatRaw = u16be(p, 16)
        return Measurement(
            weightKg = weight,
            isFinal = true, // 复合帧只在测量结束后成对下发
            fatRatio = if (fatRaw in 1..999) fatRaw / 10.0 else null,
        )
    }

    /**
     * 变体 A：20 字节复合帧第二包，头 `01 00`。
     * 肌肉/骨骼/水分均为小端 u16 ×0.1（openScale 曾因端序误判出"1997kg"，此处按现行主分支口径）。
     */
    fun parseCompositeSecond(p: ByteArray): Measurement? {
        val muscle = u16le(p, 2)
        val bone = u16le(p, 6)
        val water = u16le(p, 8)
        if (muscle == 0 && bone == 0 && water == 0) return null
        return Measurement(
            weightKg = 0.0,
            isFinal = true,
            muscleRatio = if (muscle in 1..999) muscle / 10.0 else null,
            boneKg = if (bone in 1..999) bone / 10.0 else null,
            waterRatio = if (water in 1..999) water / 10.0 else null,
        )
    }

    /**
     * 变体 B：20 字节 `AC 27` 帧。字节 2 为类型：0x00 体重流（0x80 稳定）、
     * 0x01/0x02 体脂结果帧（含阻抗与复述体重）。体重为 24 位大端，
     * 高 6 位是固件相关标签（实测常见 26 或 35），低 18 位为克数。
     */
    private fun parseAc27(p: ByteArray): Measurement? {
        // 字节 2 低 7 位是帧类型，最高位是"已稳定"标志（体重流 0x00→0x80）
        val byte2 = p[2].toInt() and 0xFF
        val type = byte2 and 0x7F
        val stable = (byte2 and 0x80) != 0
        return when (type) {
            0x00 -> parseAc27Weight(p, stable)
            0x01 -> parseAc27Result(p, altWeightOffset = 8)
            0x02 -> parseAc27Result(p, altWeightOffset = 10)
            else -> null
        }
    }

    private fun parseAc27Weight(p: ByteArray, stable: Boolean): Measurement? {
        val raw = u24be(p, 3)
        val tag = raw ushr 18
        if (tag != TAG_26 && tag != TAG_35) return null
        val grams = raw and 0x3FFFF
        if (grams == 0) return null
        return Measurement(weightKg = grams / 1000.0, isFinal = stable)
    }

    /**
     * AC 27 结果帧（type 0x01/0x02）：稳定后约 3 秒下发，携带阻抗（体重常为空，由引擎用稳定帧值兜底）。
     * 本机实测：阻抗在字节 4-5（u16 大端，原始 Ω）；字节 17-18 是序列/校验位不是阻抗。
     * 兼容保留同家族其他偏移候选，取落在成年人合理区间 [150,900]Ω 者。
     */
    private fun parseAc27Result(p: ByteArray, altWeightOffset: Int): Measurement? {
        val primaryRaw = u24be(p, 3)
        val primaryTagOk = (primaryRaw ushr 18) == TAG_26 || (primaryRaw ushr 18) == TAG_35
        val altRaw = u24be(p, altWeightOffset)
        val altTagOk = (altRaw ushr 18) == TAG_26 || (altRaw ushr 18) == TAG_35

        val grams = when {
            primaryTagOk && primaryRaw and 0x3FFFF > 0 -> primaryRaw and 0x3FFFF
            altTagOk && altRaw and 0x3FFFF > 0 -> altRaw and 0x3FFFF
            else -> 0 // 纯阻抗帧：体重位全空，合法
        }

        val c4 = u16be(p, 4).toDouble()          // 本机实测：原始 Ω
        val c6 = u16be(p, 6).toDouble()          // AFU-WL-TZ-A1 口径
        val c17 = u16be(p, 17) / 5.532           // MY_SCALE 口径（17-18 在部分固件是序列号，慎信）
        val impedance: Double?
        val sourceOffset: Int
        when {
            c4 in IMPEDANCE_RANGE -> { impedance = c4; sourceOffset = 4 }
            c6 in IMPEDANCE_RANGE -> { impedance = c6; sourceOffset = 6 }
            c17 in IMPEDANCE_RANGE -> { impedance = c17; sourceOffset = 17 }
            else -> { impedance = null; sourceOffset = -1 }
        }
        return Measurement(
            weightKg = grams / 1000.0,
            isFinal = true,
            impedanceOhm = impedance,
            impedanceSourceOffset = sourceOffset,
            isResultFrame = true,
        )
    }

    private fun u16be(p: ByteArray, off: Int) =
        ((p[off].toInt() and 0xFF) shl 8) or (p[off + 1].toInt() and 0xFF)

    private fun u16le(p: ByteArray, off: Int) =
        ((p[off + 1].toInt() and 0xFF) shl 8) or (p[off].toInt() and 0xFF)

    private fun u24be(p: ByteArray, off: Int) =
        ((p[off].toInt() and 0xFF) shl 16) or ((p[off + 1].toInt() and 0xFF) shl 8) or
            (p[off + 2].toInt() and 0xFF)

    /**
     * 变体 A 的 FFB1 握手配置包序列已随引擎改走 AC 27 档案命令而移除：
     * 变体 A 秤的自报成分（体脂/肌肉/骨量/水分）由引擎在稳定后经
     * [BodyFatCalculator.resolve] 的秤自报体脂路径入库，无需向其下发配置。
     */

    /**
     * 变体 B 的用户档案下发命令（20 字节）。秤收到档案后才会进行 BIA 测量并回发阻抗结果帧。
     * 布局：AC 27 01 00 [性别1男2女] [身高cm] [参考体重×100 u16BE] [年龄] 01 00×8 D1 [校验]，
     * 校验 = (字节2..18 求和) & 0x1F —— 该公式已对照本机真实体重帧逐帧验证。
     */
    fun buildAc27ProfileCommand(
        sexMale: Boolean,
        age: Int,
        heightCm: Int,
        refWeightKg: Double,
    ): ByteArray {
        val w100 = (refWeightKg * 100).toInt().coerceIn(0, 0xFFFF)
        val b = ByteArray(20)
        b[0] = 0xAC.toByte()
        b[1] = 0x27
        b[2] = 0x01
        b[3] = 0x00
        b[4] = (if (sexMale) 1 else 2).toByte()
        b[5] = heightCm.coerceIn(0, 0xFF).toByte()
        b[6] = (w100 shr 8).toByte()
        b[7] = (w100 and 0xFF).toByte()
        b[8] = age.coerceIn(0, 0xFF).toByte()
        b[9] = 0x01
        // b10..b17 保留 0
        b[18] = 0xD1.toByte()
        b[19] = ac27Checksum(b)
        return b
    }

    private fun ac27Checksum(frame: ByteArray): Byte {
        var sum = 0
        for (i in 2..18) sum += frame[i].toInt() and 0xFF
        return (sum and 0x1F).toByte()
    }
}
