package com.example.weight.data.sync

import android.content.Context
import androidx.work.*
import org.koin.core.context.GlobalContext
import java.util.concurrent.TimeUnit

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = if (GlobalContext.get().get<SyncRepository>().synchronize()) Result.success() else Result.retry()
    companion object {
        private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        fun enqueue(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork("account-sync", ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(network).setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build())
        }
        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("account-sync-periodic", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES).setConstraints(network).build())
            enqueue(context)
        }
    }
}
