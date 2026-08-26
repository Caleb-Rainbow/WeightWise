package com.example.weight.data.record

import com.example.weight.data.LocalStorageData
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.Flow

/**
 * 日期归组的固定北京时区偏移，与 [RecordDao] SQL 里的 '+8 hours' 同源同口径：
 * 刻意不随系统时区变化——按常住时区归日，跨时区旅行期间图表日期仍然稳定。
 */
val BEIJING_OFFSET: ZoneOffset = ZoneOffset.ofHours(8)

/** 每日统计口径：多次称重时取哪条/怎么算，影响首页图表、报告与小组件趋势 */
enum class DailyStatMode(val label: String) {
    /** 早晨首条：当天最早一次称重，最接近晨起空腹基准 */
    MORNING_FIRST("早晨首条"),

    /** 当天最后一条：晚间口径，反映一天结束时的状态 */
    LAST("当天最后一条"),

    /** 最低值：抗晚间波动的默认口径（历史行为） */
    MIN("最低值"),

    /** 平均值：全天均值，平滑单次测量噪声 */
    AVG("平均值"),
    ;

    companion object {
        /** MMKV 存 name；非法值（手工改库/降级）回退默认 MIN */
        fun fromId(id: String): DailyStatMode = entries.firstOrNull { it.name == id } ?: MIN
    }
}

/** 按日聚合后的单日代表值；[value] 为该日按口径算出的体重，[timestamp] 为代表时刻（AVG 取当日最后一条） */
data class DailyWeight(
    val value: Double,
    val recordDay: String, // Format: YYYY-MM-DD（北京日）
    val timestamp: Long,
)

/** 聚合原始输入的轻量投影：DAO 直出，不带日志/成分等大字段 */
data class RecordWeightRaw(
    val timestamp: Long,
    val weight: Double,
)

/**
 * 每日体重聚合器：把按时间升序的原始称重按北京日分组，按 [DailyStatMode] 折算单日代表值。
 * 纯函数便于单测；SQL 只负责时间窗过滤（[RecordDao.getWeightsRaw]），口径选择全部在这层完成。
 */
object DailyWeightAggregator {

    fun aggregate(rows: List<RecordWeightRaw>, mode: DailyStatMode): List<DailyWeight> =
        rows.groupBy { it.timestamp.toBeijingDay() }
            .entries
            .sortedBy { it.key }
            .map { (day, dayRows) ->
                // rows 整体升序，组内也升序：首条=最早、末条=最晚
                val picked = when (mode) {
                    DailyStatMode.MORNING_FIRST -> dayRows.first()
                    DailyStatMode.LAST -> dayRows.last()
                    DailyStatMode.MIN -> dayRows.minWith(compareBy({ it.weight }, { it.timestamp }))
                    DailyStatMode.AVG -> dayRows.last()
                }
                DailyWeight(
                    value = if (mode == DailyStatMode.AVG) dayRows.map { it.weight }.average() else picked.weight,
                    recordDay = day,
                    timestamp = picked.timestamp,
                )
            }

    private fun Long.toBeijingDay(): String =
        Instant.ofEpochMilli(this).atOffset(BEIJING_OFFSET).toLocalDate().toString()
}

/** 按当前口径聚合自 [startMillis]（含）以来的每日体重；口径切换时经 MMKV Flow 自动重发 */
fun RecordDao.dailyWeightsSince(startMillis: Long): Flow<List<DailyWeight>> =
    combine(
        getWeightsRaw(startMillis, Long.MAX_VALUE),
        LocalStorageData.dailyStatMode,
    ) { rows, mode ->
        DailyWeightAggregator.aggregate(rows, DailyStatMode.fromId(mode))
    }

/** 按当前口径聚合 [startMillis]（含）至 [endMillis]（排他）的每日体重，一次性读取 */
fun RecordDao.dailyWeightsBetween(startMillis: Long, endMillis: Long): Flow<List<DailyWeight>> =
    combine(
        getWeightsRaw(startMillis, endMillis),
        LocalStorageData.dailyStatMode,
    ) { rows, mode ->
        DailyWeightAggregator.aggregate(rows, DailyStatMode.fromId(mode))
    }
