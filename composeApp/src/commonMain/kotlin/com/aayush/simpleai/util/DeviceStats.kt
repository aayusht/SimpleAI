package com.aayush.simpleai.util

data class DeviceStats(
    val availableStorage: Long,
    val maxMemory: Long,
)

interface DeviceStatsProvider {
    fun getDeviceStats(): DeviceStats
}

expect fun createDeviceStatsProvider(): DeviceStatsProvider
