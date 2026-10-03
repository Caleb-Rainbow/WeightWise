package com.example.weight.data.record

import com.example.weight.util.TimeUtils
import java.util.Locale

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
    FAT_RATIO("fatRatio", "体脂率（估算）", "%", 1),
    WATER("waterRatio", "秤端水分率", "%", 1),
    MUSCLE_MASS("muscleMass", "秤端肌肉量", "kg", 1),
    FFM("ffm", "去脂体重（估算）", "kg", 1),
    BONE("boneMass", "秤端骨量", "kg", 1),
    IMPEDANCE("impedance", "原始阻抗", "Ω", 0);

    /** 从成分快照提取本指标数值；0 表示未测得返回 null（该日不计入序列）。 */
    fun valueOf(c: BodyComposition): Double? = c.rawValueOf(key)

    /** 按本指标小数位格式化数值（整数指标不带小数尾零） */
    fun formatValue(v: Double): String =
        if (decimals == 0) v.toLong().toString() else String.format(Locale.CHINA, "%.1f", v)

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
        val day = TimeUtils.beijingDate(raw.timestamp).toString()
        if (day != lastDay) points.add(MetricPoint(day, raw.timestamp, composition))
        else points[points.lastIndex] = MetricPoint(day, raw.timestamp, composition)
        lastDay = day
    }
    return points
}

/** 只比较最近连续的同口径记录，不跨设备、公式版本、档案切换或未知来源连线。 */
fun comparableMetricSeries(points: List<MetricPoint>, metric: TrendMetric): List<MetricPoint> {
    val latestIndex = points.indexOfLast { metric.valueOf(it.composition) != null }
    if (latestIndex < 0) return emptyList()
    val latest = points[latestIndex]
    val key = comparisonKey(latest.composition, metric) ?: return listOf(latest)
    return points.take(latestIndex + 1)
        .takeLastWhile { comparisonKey(it.composition, metric) == key }
        .filter { metric.valueOf(it.composition) != null }
}

private fun comparisonKey(c: BodyComposition, metric: TrendMetric): String? {
    val input = c.inputs ?: return null
    if (c.algorithmVersion < 2 || !input.hasValidProfile) return null
    if (metric == TrendMetric.IMPEDANCE) {
        if (input.sourceId.isBlank() || input.biaMethod == BiaMethod.UNKNOWN) return null
        return "impedance:${input.sourceId}:${input.biaMethod}"
    }
    val deviceMetric = metric in setOf(TrendMetric.WATER, TrendMetric.MUSCLE_MASS, TrendMetric.BONE)
    if ((deviceMetric || c.fatMethod == "scale_reported") && input.sourceId.isBlank()) return null
    val source = if (deviceMetric || c.fatMethod == "scale_reported") input.sourceId else ""
    val method = if (deviceMetric) "scale_reported" else c.fatMethod
    val age = if (method == "rfm") "" else input.age.toString()
    return "${c.algorithmVersion}:$method:$source:${input.biaMethod}:${input.sexMale}:${input.heightCm}:$age"
}
