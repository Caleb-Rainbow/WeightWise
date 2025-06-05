package com.example.weight

import android.app.Application
import androidx.room.Room
import com.example.weight.data.AppDataBase
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
}