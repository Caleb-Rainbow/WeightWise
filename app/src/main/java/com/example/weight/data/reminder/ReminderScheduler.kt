package com.example.weight.data.reminder

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.weight.data.LocalStorageData
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * 每日称重提醒调度：WorkManager PeriodicWork（周期 1 天）。
 * 选 WorkManager 而非 AlarmManager：免精确闹钟权限、重启自动恢复；
 * 代价是 Doze 下可能有约 10 分钟级延迟，对体重提醒可接受。
 * 周期任务由 WorkManager 自动续排，不再用「发完通知再 OneTime 自排」的链：
 * 自排链在 doWork 与续排落库之间进程被杀会永久断链。
 */
object ReminderScheduler {

    private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")

    /** 解析 HH:mm；非法值回退默认时间 */
    fun parseReminderTime(time: String): LocalTime =
        runCatching { LocalTime.parse(time, TIME_FORMAT) }.getOrDefault(LocalTime.of(7, 30))

    /** 计算从 [now] 到下一个提醒时刻的延迟（今天已过则顺延到明天） */
    fun delayUntilNext(now: LocalDateTime, reminderTime: LocalTime): Duration {
        val next = LocalDate.from(now).atTime(reminderTime)
        return if (next.isAfter(now)) Duration.between(now, next)
        else Duration.between(now, next.plusDays(1))
    }

    /** 排入（或以新时刻替换）周期提醒 */
    fun schedule(context: Context, time: String = LocalStorageData.reminderTime.value) {
        val delay = delayUntilNext(LocalDateTime.now(), parseReminderTime(time))
        val workManager = WorkManager.getInstance(context)
        // CANCEL_AND_REENQUEUE 内部原子地取消同名旧任务（含历史 OneTime 自排链、已结束的残留）
        // 再入队新周期任务；KEEP 遇到残留的 SUCCEEDED/CANCELLED 同名 work 会直接跳过，导致排程静默失败
        workManager.enqueueUniquePeriodicWork(
            ReminderWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
            PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(delay)
                .build(),
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(ReminderWorker.WORK_NAME)
    }
}
