package com.example.weight

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import androidx.work.*
import com.tencent.mmkv.MMKV

/** Instrumentation has a separate preferences root and a no-op scheduler. */
class SyncTestApplication : Application(), Configuration.Provider {
    override fun onCreate() {
        super.onCreate()
        MMKV.initialize(this, filesDir.resolve("sync-instrumentation-preferences").absolutePath)
    }
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(object : WorkerFactory() {
            override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker =
                object : Worker(appContext, workerParameters) { override fun doWork(): Result = Result.success() }
        }).build()
}
class SyncTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader, className: String, context: Context): Application =
        super.newApplication(cl, SyncTestApplication::class.java.name, context)
}
