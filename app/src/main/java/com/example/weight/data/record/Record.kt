package com.example.weight.data.record

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// timestamp 索引：首页/报告/小组件的时间窗查询、最新记录、分页排序都依赖它，无索引时全部退化为全表扫描
@Entity(indices = [Index("timestamp")])
data class Record(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val weight: Double,
    @ColumnInfo(defaultValue = "")
    val log:String,
    val timestamp: Long,
    /** 体脂秤测得的身体成分（JSON，[BodyComposition]）；空串表示手动记录/秤未测体脂 */
    @ColumnInfo(defaultValue = "")
    val bodyComposition: String = "",
    /** 体脂率 %（迁移 11 起从 JSON 冗余落列，供 SQL 层过滤/排序；0.0=未测得） */
    @ColumnInfo(defaultValue = "0.0")
    val fatRatio: Double = 0.0,
    /** 肌肉率 %（同上） */
    @ColumnInfo(defaultValue = "0.0")
    val muscleRatio: Double = 0.0,
    /** 水分率 %（同上） */
    @ColumnInfo(defaultValue = "0.0")
    val waterRatio: Double = 0.0,
) {
    companion object {
        /**
         * 秤测量/手动记录统一构造入口：JSON 存全部 14 项成分，
         * 高频三率同步落列，两处写入点（ScaleBleEngine/MainDialog）共用一套双写逻辑。
         */
        fun create(weight: Double, log: String, timestamp: Long, composition: BodyComposition?): Record =
            Record(
                weight = weight,
                log = log,
                timestamp = timestamp,
                bodyComposition = composition?.let { BodyCompositionJson.encode(it) } ?: "",
                fatRatio = composition?.fatRatio ?: 0.0,
                muscleRatio = composition?.muscleRatio ?: 0.0,
                waterRatio = composition?.waterRatio ?: 0.0,
            )
    }
}

/** 成分趋势取数轻量投影：只取 (timestamp, bodyComposition)，不物化日志等无关字段 */
data class RecordCompositionRaw(
    val timestamp: Long,
    val bodyComposition: String,
)

/** 列级成分投影（迁移 11 起）：高频三率直接读列，不物化 JSON */
data class RecordMetricRaw(
    val timestamp: Long,
    val fatRatio: Double,
    val muscleRatio: Double,
    val waterRatio: Double,
)
