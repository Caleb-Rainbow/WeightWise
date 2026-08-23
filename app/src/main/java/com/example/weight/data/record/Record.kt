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
)

data class DailyMinWeight(
    val minWeight: Double,
    val recordDay: String, // Format: YYYY-MM-DD
    val timestamp: Long
)

/** 成分趋势取数轻量投影：只取 (timestamp, bodyComposition)，不物化日志等无关字段 */
data class RecordCompositionRaw(
    val timestamp: Long,
    val bodyComposition: String,
)

