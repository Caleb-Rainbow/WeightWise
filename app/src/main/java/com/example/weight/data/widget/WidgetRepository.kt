package com.example.weight.data.widget

import com.example.weight.data.LocalStorageData
import com.example.weight.data.record.DailyMinWeight
import com.example.weight.data.record.Record
import com.example.weight.data.record.RecordDao
import com.example.weight.util.GoalProgressCalculator
import com.example.weight.util.RecordStreakCalculator
import com.example.weight.util.TimeUtils.getStartTimeForLastDays
import com.example.weight.util.WeightPredictor
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import org.koin.core.annotation.Single
import java.time.LocalDate

/** 桌面小组件展示数据。currentWeight 为 null 表示无任何记录（空状态） */
data class WeightWidgetData(
    val currentWeight: Double?,
    /** 近 7 天窗口内最早一天的最低体重，作为「较 7 天前」的对比基准 */
    val baselineWeight: Double?,
    val targetWeight: Double,
    /** 目标进度百分比；目标未设置时为 null（不展示进度条） */
    val progressPercent: Int?,
    val updatedAt: Long,
    /** 近 7 天每日最低体重序列（按天升序），供宽卡趋势线与均值使用 */
    val dailyWeights: List<DailyMinWeight> = emptyList(),
    /** BMI（当前体重 + 档案身高）；身高未配置时为 null */
    val bmi: Double? = null,
    /** 连续打卡天数（口径同首页） */
    val currentStreak: Int = 0,
    /** 按当前趋势预测距目标剩余天数；数据不足/趋势停滞时为 null */
    val estimatedDays: Long? = null,
)

/**
 * 组装桌面小组件数据：最近体重、7 天对比基准、目标进度、BMI、连续打卡、趋势预测。
 * 与首页取数同一套 DAO/工具口径，保证组件和 App 内展示一致。
 */
@Single
class WidgetRepository(private val recordDao: RecordDao) {

    suspend fun load(): WeightWidgetData = coroutineScope {
        // 一次拉 90 天序列两用：切 7 天窗口给趋势线/基准，全量给 WeightPredictor（其内部自带 90 天窗口）
        // 三次独立查询并行执行；Room suspend 自带 IO 调度，不再额外 withContext
        val currentDeferred = async { recordDao.getLastData() }
        val dailyDeferred = async {
            recordDao.getDailyMinWeightSince(getStartTimeForLastDays(PREDICT_WINDOW_DAYS.toInt())).first()
        }
        val firstDeferred = async { recordDao.getFirstData() }
        val recordDaysDeferred = async { recordDao.getRecordDaysFlow().first() }
        val current: Record? = currentDeferred.await()
        val allDays: List<DailyMinWeight> = dailyDeferred.await()
        val first: Record? = firstDeferred.await()
        val last7Days = allDays.filter { it.timestamp >= getStartTimeForLastDays(7) }
        val currentWeight = current?.weight
        val targetWeight = LocalStorageData.targetWeight.value
        val configuredStartWeight = LocalStorageData.startWeight.value
        val height = LocalStorageData.height.value
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
            dailyWeights = last7Days,
            bmi = if (currentWeight != null && height > 0.0) {
                currentWeight / (height / 100.0).let { it * it }
            } else null,
            currentStreak = RecordStreakCalculator.calculate(recordDaysDeferred.await(), LocalDate.now()).currentStreak,
            estimatedDays = if (currentWeight != null && targetWeight > 0) {
                WeightPredictor.estimateDaysToTarget(
                    dailyWeights = allDays,
                    currentWeight = currentWeight,
                    targetWeight = targetWeight,
                )
            } else null,
        )
    }

    companion object {
        /** 与 WeightPredictor.ANALYSIS_WINDOW_DAYS 一致的取数窗口 */
        private const val PREDICT_WINDOW_DAYS = 90
    }
}
