package com.example.weight.data.reminder

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.weight.data.LocalStorageData
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * 每日称重提醒调度：WorkManager OneTime 自排链。
 * 选 WorkManager 而非 AlarmManager：免精确闹钟权限、重启自动恢复；
 * 代价是 Doze 下可能有约 10 分钟级延迟，对体重提醒可接受。
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

    /** 排入（或以新时刻替换）下一次提醒 */
    fun schedule(context: Context, time: String = LocalStorageData.reminderTime.value) {
        val delay = delayUntilNext(LocalDateTime.now(), parseReminderTime(time))
        WorkManager.getInstance(context).enqueueUniqueWork(
            ReminderWorker.WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInitialDelay(delay)
                .build(),
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(ReminderWorker.WORK_NAME)
    }
}
