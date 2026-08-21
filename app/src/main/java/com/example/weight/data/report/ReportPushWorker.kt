package com.example.weight.data.report

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.weight.MainActivity
import com.example.weight.R
import com.example.weight.data.LocalStorageData
import com.example.weight.data.record.DailyMinWeight
import com.example.weight.data.record.RecordDao
import com.example.weight.util.ReportType
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.koin.core.context.GlobalContext
import java.text.DecimalFormat

/**
 * 周报推送 Worker：聚合上周数据发一条通知。周期续排由 WorkManager 的 PeriodicWork 机制自动完成。
 * 上周无打卡记录时不发通知（避免骚扰）。通知渠道在 App.onCreate 一次性创建。
 */
class ReportPushWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        // 用户中途关闭了推送：不再发通知；周期任务由 Scheduler.cancel 移除
        if (!LocalStorageData.weeklyReportPushEnabled.value) return@withContext Result.success()

        showNotification()
        Result.success()
    }

    /** 读取某个周一所在周的每日最低体重；调用方保证在 IO 线程 */
    private suspend fun readWeekWeights(monday: LocalDate): List<DailyMinWeight> = runCatching {
        val dao = GlobalContext.get().get<RecordDao>()
        val (start, end) = ReportType.WEEK.periodRange(monday)
        dao.getDailyMinWeightBetween(start, end).first()
    }.getOrDefault(emptyList())

    private suspend fun showNotification() {
        val context = applicationContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return // 没有通知权限就静默跳过，但保持续排，避免用户补授权后彻底失效
        }

        // 上周与上上周两次窗口查询互不依赖，并行执行
        val lastMonday = ReportType.WEEK.anchorOf(LocalDate.now()).minusWeeks(1)
        val (lastWeek, prevWeek) = coroutineScope {
            val lastDeferred = async { readWeekWeights(lastMonday) }
            val prevDeferred = async { readWeekWeights(lastMonday.minusWeeks(1)) }
            lastDeferred.await() to prevDeferred.await()
        }
        val contentText = WeeklyReportTextBuilder.build(lastWeek, prevWeek)
            ?: return // 上周无打卡，不打扰

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_REPORT, true)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.icon_logo)
            .setContentTitle("上周体重报告")
            .setContentText(contentText)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val WORK_NAME = "weekly_report_push"
        const val CHANNEL_ID = "weekly_report"
        private const val NOTIFICATION_ID = 1002
    }
}

/** 周报通知文案构建：纯函数便于单测 */
internal object WeeklyReportTextBuilder {

    private val signedFormat = DecimalFormat("+#.#;-#.#")
    private val plainFormat = DecimalFormat("#.#")

    /**
     * @param lastWeekWeights 上周每日最低体重（时间升序）
     * @param prevWeekWeights 上上周每日最低体重，供对比；空列表表示无对比
     * @return 通知正文；上周无打卡记录返回 null（不发通知）
     */
    fun build(lastWeekWeights: List<DailyMinWeight>, prevWeekWeights: List<DailyMinWeight>): String? {
        if (lastWeekWeights.isEmpty()) return null
        val netChange = lastWeekWeights.last().minWeight - lastWeekWeights.first().minWeight
        val base = "上周打卡 ${lastWeekWeights.size} 天，体重 ${signedFormat.format(netChange)}kg"
        if (prevWeekWeights.isEmpty()) return base
        val prevNet = prevWeekWeights.last().minWeight - prevWeekWeights.first().minWeight
        val vs = netChange - prevNet
        if (kotlin.math.abs(vs) < 0.05) return base
        val vsText = if (vs < 0) "比前一周多降 ${plainFormat.format(-vs)}kg" else "比前一周少降 ${plainFormat.format(vs)}kg"
        return "$base，$vsText"
    }
}
