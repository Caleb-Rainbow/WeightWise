package com.example.weight.data.record

import java.time.Instant
import java.time.ZoneOffset

/**
 * 成分趋势页的指标定义与每日聚合口径（纯函数，供 UI 与单测共用）。
 *
 * 指标集合 = [BodyComposition.metricItems] 的数值子集（体型是文本判定，无趋势意义），
 * 排序沿用其重要性顺序；key 与 [MetricGuide] 的解读定义一一对应。
 */
enum class TrendMetric(
    val key: String,
    val label: String,
    val unit: String,
    /** 数值展示小数位：等级/得分/阻抗为整数，率类与量类一位小数 */
    val decimals: Int,
) {
    BODY_SCORE("bodyScore", "身体得分", "分", 0),
    FAT_RATIO("fatRatio", "体脂率", "%", 1),
    VISCERAL_FAT("visceralFatLevel", "内脏脂肪", "级", 0),
    SUBCUTANEOUS_FAT("subcutaneousFatRatio", "皮下脂肪率", "%", 1),
    WATER("waterRatio", "水分率", "%", 1),
    MUSCLE_MASS("muscleMass", "肌肉量", "kg", 1),
    SKELETAL_MUSCLE_MASS("skeletalMuscleMass", "骨骼肌量", "kg", 1),
    SKELETAL_MUSCLE_RATIO("skeletalMuscleRatio", "骨骼肌率", "%", 1),
    PROTEIN("proteinRatio", "蛋白质率", "%", 1),
    FFM("ffm", "去脂体重", "kg", 1),
    BONE("boneMass", "骨量", "kg", 1),
    IMPEDANCE("impedance", "阻抗", "Ω", 0);

    /** 从成分快照提取本指标数值；0 表示未测得返回 null（该日不计入序列）。 */
    fun valueOf(c: BodyComposition): Double? = when (this) {
        FAT_RATIO -> c.fatRatio.takeIf { it > 0 }
        VISCERAL_FAT -> c.visceralFatLevel.takeIf { it > 0 }?.toDouble()
        SUBCUTANEOUS_FAT -> c.subcutaneousFatRatio.takeIf { it > 0 }
        WATER -> c.waterRatio.takeIf { it > 0 }
        MUSCLE_MASS -> c.muscleMass.takeIf { it > 0 }
        SKELETAL_MUSCLE_MASS -> c.skeletalMuscleMass.takeIf { it > 0 }
        SKELETAL_MUSCLE_RATIO -> c.skeletalMuscleRatio.takeIf { it > 0 }
        PROTEIN -> c.proteinRatio.takeIf { it > 0 }
        FFM -> c.ffm.takeIf { it > 0 }
        BONE -> c.boneMass.takeIf { it > 0 }
        BODY_SCORE -> c.bodyScore.takeIf { it > 0 }?.toDouble()
        IMPEDANCE -> c.impedance.takeIf { it > 0 }?.toDouble()
    }

    /** 按本指标小数位格式化数值（整数指标不带小数尾零） */
    fun formatValue(v: Double): String =
        if (decimals == 0) v.toLong().toString() else String.format("%.1f", v)

    companion object {
        fun fromKey(key: String): TrendMetric? = entries.firstOrNull { it.key == key }
    }
}

/** 趋势图上的一个数据点：一天一条成分快照 */
data class MetricPoint(
    /** 北京时间 yyyy-MM-dd，与体重趋势图的 recordDay 口径一致 */
    val day: String,
    val timestamp: Long,
    val composition: BodyComposition,
)

/**
 * 每日成分聚合：按北京时间（固定 +8，与 DAO 的 `DATE(..., '+8 hours')` 分区同口径）
 * 每天取最后一条有效成分快照——同日多次称重时以当天最后一次为准。
 * 坏 JSON / 全零成分（hasAny=false）的记录直接跳过，不产生数据点。
 */
fun dailyLastCompositions(raws: List<RecordCompositionRaw>): List<MetricPoint> {
    if (raws.isEmpty()) return emptyList()
    val points = ArrayList<MetricPoint>(raws.size)
    var lastDay: String? = null
    // raws 已由 DAO 按 timestamp 升序返回：同日靠后的记录自然覆盖先写入的点
    for (raw in raws) {
        val composition = BodyCompositionJson.decode(raw.bodyComposition) ?: continue
        if (!composition.hasAny) continue
        val day = Instant.ofEpochMilli(raw.timestamp).atZone(BEIJING).toLocalDate().toString()
        if (day != lastDay) points.add(MetricPoint(day, raw.timestamp, composition))
        else points[points.lastIndex] = MetricPoint(day, raw.timestamp, composition)
        lastDay = day
    }
    return points
}

private val BEIJING = ZoneOffset.ofHours(8)
