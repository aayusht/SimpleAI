package com.aayush.simpleai.di

import com.aayush.simpleai.util.AndroidDownloadProvider
import com.aayush.simpleai.util.DownloadProvider
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module = module {
    single<DownloadProvider> { AndroidDownloadProvider(get()) }
}
