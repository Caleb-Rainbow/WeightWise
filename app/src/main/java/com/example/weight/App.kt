package com.example.weight

import android.app.Application
import org.koin.android.ext.koin.androidContext
import org.koin.plugin.module.dsl.startKoin

class App: Application() {

    override fun onCreate() {
        super.onCreate()
        startKoin<KoinApp> {
            androidContext(this@App)
        }
    }
}
