package com.aayush.simpleai.di

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import org.koin.core.module.Module
import org.koin.dsl.module
import io.ktor.client.*
import com.aayush.simpleai.db.AppDatabase
import com.aayush.simpleai.db.ChatHistoryDao
import com.aayush.simpleai.db.getDatabaseBuilder
import com.aayush.simpleai.util.createDownloadClient
import com.aayush.simpleai.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.KoinAppDeclaration

expect val platformModule: Module

val sharedModule = module {
    single<HttpClient> { createDownloadClient() }
    single<AppDatabase> {
        getDatabaseBuilder()
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
    }
    single<ChatHistoryDao> { get<AppDatabase>().chatHistoryDao() }
    viewModel { MainViewModel(get(), get(), get(), get()) }
}

fun initKoin(appDeclaration: KoinAppDeclaration = {}) =
    startKoin {
        appDeclaration()
        modules(sharedModule, platformModule)
    }
