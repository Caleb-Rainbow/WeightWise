package com.example.weight.data.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
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
import com.example.weight.data.record.RecordDao
import com.example.weight.util.RecordStreakCalculator
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.koin.core.context.GlobalContext

/**
 * 每日称重提醒 Worker：发一条通知，然后自排下一次（+24h）。
 * 连锁由 [ReminderScheduler.schedule] 发起，Worker 内部续排保持链不断。
 */
class ReminderWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        // 用户中途关闭了提醒：不再发通知也不再续排
        if (!LocalStorageData.reminderEnabled.value) return@withContext Result.success()

        showNotification()
        // 续排明天同一时刻（不再携带 KEY_FROM_USER_SCHEDULE，区分用户手动改点与自动续排）
        val delay = ReminderScheduler.delayUntilNext(
            java.time.LocalDateTime.now(),
            ReminderScheduler.parseReminderTime(LocalStorageData.reminderTime.value),
        )
        androidx.work.WorkManager.getInstance(applicationContext).enqueueUniqueWork(
            WORK_NAME,
            androidx.work.ExistingWorkPolicy.REPLACE,
            androidx.work.OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInitialDelay(delay)
                .build(),
        )
        Result.success()
    }

    private suspend fun showNotification() {
        val context = applicationContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return // 没有通知权限就静默跳过，但保持续排，避免用户补授权后彻底失效
        }

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "称重提醒",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "每日定时提醒记录体重" }
        )

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_ADD_DIALOG, true)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val streakText = readStreakText()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.icon_logo)
            .setContentTitle("该称体重啦")
            .setContentText(streakText)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    /** 通知文案带上当前连续打卡天数，多一层激励 */
    private suspend fun readStreakText(): String = runCatching {
        val dao = GlobalContext.get().get<RecordDao>()
        val days = dao.getRecordDaysFlow().first()
        val streak = RecordStreakCalculator.calculate(days, LocalDate.now()).currentStreak
        if (streak >= 2) "已连续打卡 $streak 天，别断档哦" else "每天称一称，看见变化的发生"
    }.getOrDefault("每天称一称，看见变化的发生")

    companion object {
        const val WORK_NAME = "daily_weigh_reminder"
        private const val CHANNEL_ID = "weigh_reminder"
        private const val NOTIFICATION_ID = 1001
    }
}
