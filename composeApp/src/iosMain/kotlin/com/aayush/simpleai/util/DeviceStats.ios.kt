package com.aayush.simpleai.util

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.*

@OptIn(ExperimentalForeignApi::class)
class IosDeviceStatsProvider : DeviceStatsProvider {
    override fun getDeviceStats(): DeviceStats {
        // Storage
        val attributes = NSFileManager.defaultManager
            .attributesOfFileSystemForPath(path = NSHomeDirectory(), error = null)
        val availableStorage = (attributes?.get(NSFileSystemFreeSize) as? Long) ?: 0L

        // Max Memory
        val maxMemory = NSProcessInfo.processInfo.physicalMemory.toLong()

        return DeviceStats(
            availableStorage = availableStorage,
            maxMemory = maxMemory,
        )
    }
}

actual fun createDeviceStatsProvider(): DeviceStatsProvider {
    return IosDeviceStatsProvider()
}
