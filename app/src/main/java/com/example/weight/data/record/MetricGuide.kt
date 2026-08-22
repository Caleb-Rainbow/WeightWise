package com.example.weight.data.record

/**
 * 身体成分指标的解读中心：每个指标的「是什么/正常范围/当前状态」。
 *
 * 正常范围采用商用体脂秤 App 与公开文献的通行标准（性别分带）：
 * - 体脂率：参考 ACE 分类（男 10-20% / 女 18-28% 标准）
 * - 水分率：男 50-65% / 女 45-60%（生理水合参考区间，Lukaski 1986 人群反推）
 * - 内脏脂肪等级：1-9 正常 / 10-14 偏高 / ≥15 高（Omron 通行口径）
 * - 骨骼肌率：与体型九宫格同带（男 38-50% / 女 30-40%）
 * - 蛋白质率：10-20%；身体得分：60 及格 / 80 优
 * 量类指标（肌肉量/骨量/去脂体重）的正常区间与身高体重强相关，无通用标准带，
 * 仅提供含义说明；阻抗为原始测量值同理。
 */
object MetricGuide {

    enum class Status(val label: String) {
        LOW("偏低"),
        NORMAL("标准"),
        HIGH("偏高"),
        VERY_HIGH("过高"),
    }

    /** 刻度条上的一个色段：[start, end] 区间 + 该区间的状态语义（决定段色） */
    data class Segment(val start: Double, val end: Double, val status: Status)

    /**
     * 数值型指标的分段式刻度条：min..max 为显示范围，segments 连续覆盖全范围
     * （每段一种状态色：偏低蓝/标准绿/偏高琥珀/过高红），value 定位指针圆点。
     */
    data class Bar(
        val min: Double,
        val max: Double,
        val segments: List<Segment>,
        val value: Double,
    ) {
        val normalStart: Double get() = segments.first { it.status == Status.NORMAL }.start
        val normalEnd: Double get() = segments.first { it.status == Status.NORMAL }.end
    }

    /** 由分带构造刻度条：低段[min,low) / 标准段[low,normal] / 高段(normal,high] / 过高段(high,max]；零宽段过滤 */
    private fun bar(min: Double, max: Double, low: Double, normal: Double, high: Double?, value: Double): Bar =
        Bar(
            min = min,
            max = max,
            segments = buildList {
                add(Segment(min, low, Status.LOW))
                add(Segment(low, normal, Status.NORMAL))
                when (high) {
                    null -> add(Segment(normal, max, Status.HIGH))
                    else -> {
                        add(Segment(normal, high, Status.HIGH))
                        add(Segment(high, max, Status.VERY_HIGH))
                    }
                }
            }.filter { it.end - it.start > 1e-9 },
            value = value,
        )

    data class Info(
        val name: String,
        /** 该指标是什么、怎么来的 */
        val description: String,
        /** 正常范围文本（含性别口径）；量类指标为参考常识 */
        val rangeText: String,
        val status: Status?,
        val bar: Bar?,
    )

    /**
     * 按指标 key 取解读；[rawValue] 是原始数值（量纲由 key 决定），文本型/无量纲指标传 null。
     * 未知 key 返回 null（弹窗不弹）。
     */
    fun info(key: String, rawValue: Double?, sexMale: Boolean): Info? = when (key) {
        "fatRatio" -> {
            val band = if (sexMale) 10.0 to 20.0 else 18.0 to 28.0
            val high = if (sexMale) 25.0 else 33.0
            fatInfo(band, high, rawValue, sexMale)
        }
        "visceralFatLevel" -> rawValue?.let { v ->
            Info(
                name = "内脏脂肪等级",
                description = "内脏器官周围脂肪堆积程度的等级评估，与代谢健康、心脑血管风险密切相关。等级越高风险越大，是比体重更能反映健康风险的指标。",
                rangeText = "正常范围：1-9 级；10-14 级偏高；15 级及以上建议就医评估",
                status = when {
                    v <= 9 -> Status.NORMAL
                    v <= 14 -> Status.HIGH
                    else -> Status.VERY_HIGH
                },
                bar = bar(min = 1.0, max = 20.0, low = 1.0, normal = 9.0, high = 14.0, value = v),
            )
        }
        "subcutaneousFatRatio" -> {
            val band = if (sexMale) 10.0 to 25.0 else 18.0 to 33.0
            rawValue?.let { v ->
                Info(
                    name = "皮下脂肪率",
                    description = "储存在皮肤正下方的脂肪占体重的比例，主要影响身体围度与外观，对健康的直接风险低于内脏脂肪。",
                    rangeText = "常见范围：男 10-25%，女 18-33%（皮下脂肪个体差异大，仅作参考）",
                    status = when {
                        v < band.first -> Status.LOW
                        v <= band.second -> Status.NORMAL
                        else -> Status.HIGH
                    },
                    bar = bar(min = 0.0, max = 60.0, low = band.first, normal = band.second, high = null, value = v),
                )
            }
        }
        "waterRatio" -> {
            val band = if (sexMale) 50.0 to 65.0 else 45.0 to 60.0
            rawValue?.let { v ->
                Info(
                    name = "水分率",
                    description = "体内水分占体重的比例，反映水合状态。水分率长期偏低常与脂肪偏高相伴（脂肪几乎不含水），运动后及时补水可改善。",
                    rangeText = "正常范围：男 50-65%，女 45-60%",
                    status = when {
                        v < band.first -> Status.LOW
                        v <= band.second -> Status.NORMAL
                        else -> Status.HIGH
                    },
                    bar = bar(min = 30.0, max = 70.0, low = band.first, normal = band.second, high = null, value = v),
                )
            }
        }
        "muscleMass" -> Info(
            name = "肌肉量",
            description = "全身肌肉（骨骼肌＋内脏平滑肌等）的总重量，由去脂体重减骨量推得。绝对量与身高体重相关，趋势变化比单次数值更有意义。",
            rangeText = "无统一标准带：成年男性常见 45-70 kg、女性 30-50 kg，关注自身趋势即可",
            status = null,
            bar = null,
        )
        "skeletalMuscleMass" -> Info(
            name = "骨骼肌量",
            description = "附着在骨骼上、可主动收缩运动的肌肉重量（Janssen 2000 方程由阻抗推得）。骨骼肌是基础代谢的主要贡献者，增肌有助于提升每日消耗。",
            rangeText = "无统一标准带：数值随体重增长，建议结合骨骼肌率一起看",
            status = null,
            bar = null,
        )
        "skeletalMuscleRatio" -> {
            val band = if (sexMale) 38.0 to 50.0 else 30.0 to 40.0
            rawValue?.let { v ->
                Info(
                    name = "骨骼肌率",
                    description = "骨骼肌量占体重的比例，是相对体格更公平的肌肉水平指标——体重上升但骨骼肌率不降，说明增重来自肌肉。",
                    rangeText = "正常范围：男 38-50%，女 30-40%",
                    status = when {
                        v < band.first -> Status.LOW
                        v <= band.second -> Status.NORMAL
                        else -> Status.HIGH
                    },
                    bar = bar(min = 20.0, max = 60.0, low = band.first, normal = band.second, high = null, value = v),
                )
            }
        }
        "proteinRatio" -> rawValue?.let { v ->
            Info(
                name = "蛋白质率",
                description = "体内蛋白质占体重的比例，主要由肌肉量决定，反映营养状况。偏低提示肌肉量不足或蛋白质摄入不够。",
                rangeText = "正常范围：10-20%",
                status = when {
                    v < 10 -> Status.LOW
                    v <= 20 -> Status.NORMAL
                    else -> Status.HIGH
                },
                bar = bar(min = 5.0, max = 25.0, low = 10.0, normal = 20.0, high = null, value = v),
            )
        }
        "ffm" -> Info(
            name = "去脂体重",
            description = "体重减去脂肪后的重量，包含肌肉、水分、骨骼与器官（Sun 2003 方程由阻抗推得）。减脂期保持去脂体重不降，说明减的是脂肪而非肌肉。",
            rangeText = "无统一标准带：越大通常代谢越旺盛，减脂期关注它是否保持稳定",
            status = null,
            bar = null,
        )
        "boneMass" -> Info(
            name = "骨量",
            description = "骨骼中矿物质的含量估算（约占去脂体重 5-6%）。峰值在 30 岁前后，之后缓慢流失，负重运动与钙摄入有助于维持。",
            rangeText = "常见范围：1.5-4.5 kg；足部 BIA 对骨量的估算精度有限，仅作长期趋势参考",
            status = null,
            bar = null,
        )
        "bodyScore" -> rawValue?.let { v ->
            Info(
                name = "身体得分",
                description = "综合体脂率、骨骼肌率、水分率与内脏脂肪等级的透明扣分制评分：100 分起评，体脂偏离理想带每百分点扣 0.8，骨骼肌率低扣 15，水分率偏离扣 8，内脏脂肪超 9 级每级扣 2。",
                rangeText = "80 分及以上为优秀；60-79 为良好；60 以下建议调整体成分",
                status = when {
                    v >= 80 -> Status.NORMAL
                    v >= 60 -> Status.HIGH
                    else -> Status.LOW
                },
                bar = bar(min = 0.0, max = 100.0, low = 60.0, normal = 100.0, high = null, value = v),
            )
        }
        "bodyType" -> Info(
            name = "体型",
            description = "按「体脂率带 × 骨骼肌率带」九宫格判定：标准型 / 苗条型 / 纤瘦型 / 瘦弱型 / 健美型 / 肌肉型 / 肥胖型 / 结实肥胖型 / 虚胖型。虚胖型=脂肪高而肌肉少，是最需要「减脂＋增肌」的组合。",
            rangeText = "目标体型：标准型（体脂与骨骼肌率都在正常带内）",
            status = null,
            bar = null,
        )
        "impedance" -> Info(
            name = "阻抗",
            description = "50 kHz 微弱电流通过身体的电阻原始值，是所有成分指标的共同计算输入。脂肪含水少、电阻大，肌肉含水多、电阻小——阻抗越低通常去脂体重越高。",
            rangeText = "常见范围：男约 310-580 Ω、女约 460-690 Ω（Lukaski 1986 人群参考）",
            status = null,
            bar = null,
        )
        else -> null
    }

    private fun fatInfo(band: Pair<Double, Double>, high: Double, rawValue: Double?, sexMale: Boolean): Info? =
        rawValue?.let { v ->
            val sex = if (sexMale) "男" else "女"
            Info(
                name = "体脂率",
                description = "体内脂肪重量占体重的比例，比 BMI 更准确反映肥胖程度。包含必需脂肪（维持生理功能）与储存脂肪，女性必需脂肪天然更高。",
                rangeText = "正常范围：$sex ${band.first.toInt()}-${band.second.toInt()}%；超过 ${high.toInt()}% 属肥胖",
                status = when {
                    v < band.first -> Status.LOW
                    v <= band.second -> Status.NORMAL
                    v <= high -> Status.HIGH
                    else -> Status.VERY_HIGH
                },
                bar = bar(min = 0.0, max = 50.0, low = band.first, normal = band.second, high = high, value = v),
            )
        }
}
