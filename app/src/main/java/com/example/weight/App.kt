package com.example.weight

import android.app.Application
import com.example.weight.data.diet.DietImageMigrator
import com.example.weight.data.diet.DietRecordDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext
import org.koin.plugin.module.dsl.startKoin

class App : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        startKoin<KoinApp> {
            androidContext(this@App)
        }
        // 旧版饮食图片存于 cacheDir，清缓存即丢失；启动时迁到 filesDir 并回写数据库路径
        appScope.launch {
            val dietRecordDao = GlobalContext.get().get<DietRecordDao>()
            DietImageMigrator.migrate(this@App, dietRecordDao)
        }
    }
}
