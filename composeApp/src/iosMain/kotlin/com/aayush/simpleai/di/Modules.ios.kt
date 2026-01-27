package com.aayush.simpleai.di

import com.aayush.simpleai.util.BackgroundDownloadService
import com.aayush.simpleai.util.DownloadProvider
import com.aayush.simpleai.util.IosDownloadProvider
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module = module {
    single<DownloadProvider> { IosDownloadProvider() }
    single { BackgroundDownloadService() }
}

fun doInitKoin() {
    initKoin {}
}
