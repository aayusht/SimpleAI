package com.aayush.simpleai.di

import org.koin.core.module.Module
import org.koin.dsl.module
import io.ktor.client.*
import com.aayush.simpleai.util.createDownloadClient
import com.aayush.simpleai.MainViewModel
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.KoinAppDeclaration

expect val platformModule: Module

val sharedModule = module {
    single<HttpClient> { createDownloadClient() }
    viewModel { MainViewModel(get(), get()) }
}

fun initKoin(appDeclaration: KoinAppDeclaration = {}) =
    startKoin {
        appDeclaration()
        modules(sharedModule, platformModule)
    }
