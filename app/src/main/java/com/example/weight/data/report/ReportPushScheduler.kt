package com.example.weight.data.report

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.weight.data.LocalStorageData
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

/**
 * 周报推送调度：WorkManager OneTime 自排链，每周一固定时刻触发一次。
 * 与每日称重提醒同机制：免精确闹钟权限、重启自动恢复，
 * Doze 下可能有约 10 分钟级延迟，对周报可接受。
 */
object ReportPushScheduler {

    private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")

    /** 解析 HH:mm；非法值回退默认时间 */
    fun parsePushTime(time: String): LocalTime =
        runCatching { LocalTime.parse(time, TIME_FORMAT) }.getOrDefault(LocalTime.of(8, 0))

    /**
     * 计算从 [now] 到下一个周一 [pushTime] 的延迟。
     * 今天就是周一且时刻未过则今天发，否则顺延到下周一。
     */
    fun delayUntilNextMonday(now: LocalDateTime, pushTime: LocalTime): Duration {
        val thisMonday = now.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val todaySlot = thisMonday.atTime(pushTime)
        return if (todaySlot.isAfter(now)) Duration.between(now, todaySlot)
        else Duration.between(now, thisMonday.plusWeeks(1).atTime(pushTime))
    }

    /** 排入（或以新时刻替换）下一次周报推送 */
    fun schedule(context: Context, time: String = LocalStorageData.weeklyReportPushTime.value) {
        val delay = delayUntilNextMonday(LocalDateTime.now(), parsePushTime(time))
        WorkManager.getInstance(context).enqueueUniqueWork(
            ReportPushWorker.WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<ReportPushWorker>()
                .setInitialDelay(delay)
                .build(),
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(ReportPushWorker.WORK_NAME)
    }
}
