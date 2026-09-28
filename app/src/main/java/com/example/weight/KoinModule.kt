package com.example.weight

import android.app.Application
import android.util.Log
import androidx.room.Room
import com.example.weight.data.AppDataBase
import com.example.weight.data.MIGRATION_10_11
import com.example.weight.data.diet.DietRecordDao
import com.example.weight.data.record.RecordDao
import com.example.weight.data.scale.BodyCompositionRecalculator
import com.example.weight.data.scale.ScaleBleEngine
import com.example.weight.data.update.UpdateRemoteDataSource
import com.example.weight.data.update.UpdateRepository
import com.example.weight.ui.update.UpdateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.KoinApplication
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single
import java.util.concurrent.TimeUnit

@Module
@ComponentScan
class KoinModule {
    @Single
    fun provideAppDataBase(application: Application): AppDataBase {
        return Room.databaseBuilder(application, AppDataBase::class.java, com.example.weight.data.sync.SyncStorage.databaseName)
            .addMigrations(MIGRATION_10_11, com.example.weight.data.sync.MIGRATION_13_14)
            .build()
    }

    @Single
    fun provideRecordDao(appDataBase: AppDataBase)= appDataBase.recordDao()

    @Single
    fun provideScaleBleEngine(
        application: Application,
        appScope: CoroutineScope,
        recordDao: RecordDao,
    ): ScaleBleEngine = ScaleBleEngine(application, appScope, recordDao)

    @Single
    fun provideBodyCompositionRecalculator(recordDao: RecordDao) =
        BodyCompositionRecalculator(recordDao)

    @Single
    fun provideDietRecordDao(appDataBase: AppDataBase): DietRecordDao = appDataBase.dietRecordDao()

    @Single
    fun provideAppScope(application: Application): CoroutineScope =
        (application as App).appScope

    @Single
    fun provideChatOkHttpClient(): OkHttpClient {
        // LLM 长响应需要整体放宽；连接/读超时收紧，弱网下快速失败而不是挂满 5 分钟。
        // callTimeout 管整体请求，readTimeout 管两个 SSE chunk 之间的最大间隔
        return OkHttpClient.Builder()
            .callTimeout(5, TimeUnit.MINUTES)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.MINUTES)
            .apply {
                if (BuildConfig.DEBUG) {
                    // BASIC 只打方法/URL/状态行，不含请求头（API key）与响应体（流式 chunk）
                    addInterceptor(HttpLoggingInterceptor { message ->
                        Log.d("WW-Http", message)
                    }.apply { level = HttpLoggingInterceptor.Level.BASIC })
                }
            }
            .build()
    }

    @Single
    fun provideUpdateRemoteDataSource(json: Json): UpdateRemoteDataSource {
        // 检查更新客户端：轻量短请求，读超时收紧弱网快速失败
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
        return UpdateRemoteDataSource(client, json)
    }

    @Single
    fun provideUpdateRepository(
        application: Application,
        remoteDataSource: UpdateRemoteDataSource,
    ): UpdateRepository {
        // 下载客户端：APK 大文件，读超时放宽到 10 分钟避免慢网下载中断
        val downloadClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.MINUTES)
            .build()
        return UpdateRepository(application, downloadClient, remoteDataSource)
    }

    @Single
    fun provideUpdateManager(
        application: Application,
        updateRepository: UpdateRepository,
        appScope: CoroutineScope,
    ): UpdateManager = UpdateManager(application, updateRepository, appScope)
    @Single
    fun provideJson() = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        // prettyPrint 会让聊天请求体（含全部历史消息）与备份文件膨胀 30-50%，序列化也更慢
        prettyPrint = false
    }
}

/**
 * Koin 装配入口。
 * koin-compiler-plugin 1.1.0+ 要求 @KoinApplication 与 @Module 分离，
 * 并通过 @KoinApplication(modules=[...]) 显式装配模块；否则插件会把
 * startKoin<T> 改写为 startKoinWith(emptyList())，启动即 NoDefinitionFoundException。
 */
@KoinApplication(modules = [KoinModule::class])
class KoinApp