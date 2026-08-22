package com.example.weight

import android.app.Application
import androidx.room.Room
import com.example.weight.data.AppDataBase
import com.example.weight.data.createDefaultHttpClient
import com.example.weight.data.diet.DietRecordDao
import com.example.weight.data.record.RecordDao
import com.example.weight.data.scale.ScaleBleEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.Json
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.KoinApplication
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

@Module
@ComponentScan
class KoinModule {
    @Single
    fun provideAppDataBase(application: Application): AppDataBase {
        return Room.databaseBuilder(application, AppDataBase::class.java, "database")
            .fallbackToDestructiveMigration(false).build()
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
    fun provideDietRecordDao(appDataBase: AppDataBase): DietRecordDao = appDataBase.dietRecordDao()

    @Single
    fun provideAppScope(application: Application): CoroutineScope =
        (application as App).appScope

    @Single
    fun provideHttpClient(json: Json) = createDefaultHttpClient(json)
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