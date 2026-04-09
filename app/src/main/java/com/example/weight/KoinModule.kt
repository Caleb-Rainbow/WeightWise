package com.example.weight

import android.app.Application
import androidx.room.Room
import com.example.weight.data.AppDataBase
import com.example.weight.data.createDefaultHttpClient
import kotlinx.serialization.json.Json
import org.koin.core.annotation.ComponentScan
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
    fun provideHttpClient(json: Json) = createDefaultHttpClient(json)
    @Single
    fun provideJson() = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }
}