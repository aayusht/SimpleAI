package com.aayush.simpleai.util

import android.app.ActivityManager
import android.content.Context
import android.os.Environment
import android.os.StatFs
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class AndroidDeviceStatsProvider : DeviceStatsProvider, KoinComponent {
    private val context: Context by inject()

    override fun getDeviceStats(): DeviceStats {
        // Storage
        val stat = StatFs(Environment.getDataDirectory().path)
        val availableStorage = stat.availableBlocksLong * stat.blockSizeLong

        // Memory
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)

        return DeviceStats(
            availableStorage = availableStorage,
            maxMemory = memoryInfo.totalMem,
        )
    }
}

actual fun createDeviceStatsProvider(): DeviceStatsProvider {
    return AndroidDeviceStatsProvider()
}
