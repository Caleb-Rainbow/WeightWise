package com.example.weight

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.example.weight.data.diet.DietImageMigrator
import com.example.weight.data.diet.DietRecordDao
import com.example.weight.data.reminder.ReminderWorker
import com.example.weight.data.report.ReportPushWorker
import com.tencent.mmkv.MMKV
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext
import org.koin.plugin.module.dsl.startKoin

class App : Application() {

    /** 应用级协程域：饮食删除撤销窗口等必须活过 ViewModel 生命周期的异步工作挂这里 */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // 提前完成 MMKV 的 mmap 初始化（毫秒级），避免首帧前主线程首次访问时再做磁盘映射
        MMKV.initialize(this)
        startKoin<KoinApp> {
            androidContext(this@App)
        }
        createNotificationChannels()
        // 旧版饮食图片存于 cacheDir，清缓存即丢失；启动时迁到 filesDir 并回写数据库路径
        appScope.launch {
            val dietRecordDao = GlobalContext.get().get<DietRecordDao>()
            DietImageMigrator.migrate(this@App, dietRecordDao)
        }
    }

    /** 通知渠道一次性创建；原先每次发通知都重复 createChannel（一次多余的 binder 调用） */
    private fun createNotificationChannels() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                ReminderWorker.CHANNEL_ID,
                "称重提醒",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "每日定时提醒记录体重" }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                ReportPushWorker.CHANNEL_ID,
                "周报推送",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "每周一推送上周体重报告摘要" }
        )
    }
}
