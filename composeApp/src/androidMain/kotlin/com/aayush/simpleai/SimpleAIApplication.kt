package com.aayush.simpleai

import android.app.Application
import com.aayush.simpleai.db.initAppDatabase
import com.aayush.simpleai.di.initKoin
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger

class SimpleAIApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initAppDatabase(this)
        initKoin {
            androidLogger()
            androidContext(this@SimpleAIApplication)
        }
    }
}
