package com.example.weight.data.widget

import com.example.weight.data.LocalStorageData
import com.example.weight.data.record.DailyMinWeight
import com.example.weight.data.record.Record
import com.example.weight.data.record.RecordDao
import com.example.weight.util.GoalProgressCalculator
import com.example.weight.util.TimeUtils.getStartTimeForLastDays
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single

/** 桌面小组件展示数据。currentWeight 为 null 表示无任何记录（空状态） */
data class WeightWidgetData(
    val currentWeight: Double?,
    /** 近 7 天窗口内最早一天的最低体重，作为「较 7 天前」的对比基准 */
    val baselineWeight: Double?,
    val targetWeight: Double,
    /** 目标进度百分比；目标未设置时为 null（不展示进度条） */
    val progressPercent: Int?,
    val updatedAt: Long,
)

/**
 * 组装桌面小组件数据：最近体重、7 天对比基准、目标进度。
 * 与首页取数同一套 DAO 口径，保证组件和 App 内展示一致。
 */
@Single
class WidgetRepository(private val recordDao: RecordDao) {

    suspend fun load(): WeightWidgetData = withContext(Dispatchers.IO) {
        val current: Record? = recordDao.getLastData()
        val last7Days: List<DailyMinWeight> =
            recordDao.getDailyMinWeightSince(getStartTimeForLastDays(7)).first()
        val first: Record? = recordDao.getFirstData()
        val targetWeight = LocalStorageData.targetWeight.value
        val configuredStartWeight = LocalStorageData.startWeight.value
        val currentWeight = current?.weight
        WeightWidgetData(
            currentWeight = currentWeight,
            baselineWeight = last7Days.firstOrNull()?.minWeight,
            targetWeight = targetWeight,
            progressPercent = if (currentWeight != null) {
                GoalProgressCalculator.progressPercent(
                    // 与首页同口径：手动设置的起始体重优先，未设置时回落第一条记录
                    startWeight = GoalProgressCalculator.effectiveStartWeight(
                        configuredStartWeight, first?.weight
                    ) ?: currentWeight,
                    currentWeight = currentWeight,
                    targetWeight = targetWeight,
                )
            } else null,
            updatedAt = current?.timestamp ?: 0L,
        )
    }
}
