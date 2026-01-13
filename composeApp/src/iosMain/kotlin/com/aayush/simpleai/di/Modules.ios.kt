package com.aayush.simpleai.di

import com.aayush.simpleai.util.DownloadProvider
import com.aayush.simpleai.util.IosDownloadProvider
import com.aayush.simpleai.util.IosLlmEngineProvider
import com.aayush.simpleai.util.LlmEngineProvider
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module = module {
    single<DownloadProvider> { IosDownloadProvider() }
    single<LlmEngineProvider> { IosLlmEngineProvider() }
}

fun doInitKoin() {
    initKoin {}
}
