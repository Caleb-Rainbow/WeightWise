package com.example.weight.data.record

/** 指标来源及参考解读；模型推导或来源不明的字段不作健康诊断。 */
object MetricGuide {
    enum class Status(val label: String) {
        LOW("偏低"), NORMAL("参考带内"), HIGH("偏高"), VERY_HIGH("明显偏高"),
    }
    data class Segment(val start: Double, val end: Double, val status: Status)
    data class Bar(
        val min: Double, val max: Double, val segments: List<Segment>, val value: Double,
    ) {
        val normalStart: Double get() = segments.first { it.status == Status.NORMAL }.start
        val normalEnd: Double get() = segments.first { it.status == Status.NORMAL }.end
    }
    data class Info(
        val name: String, val description: String, val rangeText: String,
        val status: Status?, val bar: Bar?,
    )

    fun info(
        key: String, rawValue: Double?, sexMale: Boolean,
        composition: BodyComposition? = null,
    ): Info? {
        if (rawValue != null && (!rawValue.isFinite() || rawValue <= 0)) return null
        val source = composition?.sourceDescription ?: "估算值，个体误差未校准"
        fun note(name: String, description: String, reference: String) =
            Info(name, description, reference, null, null)
        return when (key) {
            "fatRatio" -> {
                val value = rawValue?.takeIf { it < 100 } ?: return null
                val low = if (sexMale) 10.0 else 18.0
                val normal = if (sexMale) 20.0 else 28.0
                val high = if (sexMale) 25.0 else 33.0
                val canInterpret = composition == null ||
                    (composition.algorithmVersion >= 2 && composition.inputs?.hasValidProfile == true &&
                        composition.inputs.hasValidWeight &&
                        composition.inputs.age in 18..83)
                Info(
                    "体脂率（估算）",
                    "体内脂肪占体重的估算比例。$source。不同来源不可直接比较，不能据单次结果判定肥胖或健康风险。",
                    if (canInterpret) "成人显示参考带：${if (sexMale) "男" else "女"} ${low.toInt()}-${normal.toInt()}%；分带不是诊断标准"
                    else "测量档案不完整或年龄不适用，不作分带判断",
                    if (!canInterpret) null else when {
                        value < low -> Status.LOW
                        value <= normal -> Status.NORMAL
                        value <= high -> Status.HIGH
                        else -> Status.VERY_HIGH
                    },
                    if (!canInterpret) null else Bar(0.0, 100.0, listOf(
                        Segment(0.0, low, Status.LOW),
                        Segment(low, normal, Status.NORMAL),
                        Segment(normal, high, Status.HIGH),
                        Segment(high, 100.0, Status.VERY_HIGH),
                    ), value),
                )
            }
            "waterRatio" -> note("秤端水分率", "秤端提供的水分比例估算，本 App 不用去脂体重固定乘常数补造。", "受设备及测量条件影响，不能据此认定缺水或要求补水")
            "muscleMass" -> note("秤端肌肉量", "按秤端肌肉率与本次体重换算；定义由设备决定，不等同去脂体重减骨量。", "只比较同一设备、同一口径；不能直接判断增肌效果")
            "ffm" -> note("去脂体重（估算）", "体重减去估算脂肪量，包含肌肉、水分、骨骼及器官。$source。", "不是独立测量，变化不等同肌肉变化")
            "boneMass" -> note("秤端骨量", "设备提供的骨量估算，未由固定去脂比例推算。", "不能用于骨密度、骨质流失或骨质疏松判定")
            "impedance" -> note("原始阻抗", "秤上报的电学测量值。脚部、手腕至脚踝等接线路径及频率不同，不能混用模型。", "原始 Ω；只比较同一设备和接线路径，无通用健康分带")
            "visceralFatLevel", "subcutaneousFatRatio", "proteinRatio",
            "skeletalMuscleMass", "skeletalMuscleRatio", "bodyScore", "bodyType" ->
                note("旧版推导指标", "旧版没有独立测量或设备校准依据，已停止生成及健康判定。", "原始存档保留，不参与当前展示或趋势")
            else -> null
        }
    }
}
