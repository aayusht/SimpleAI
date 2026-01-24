package com.aayush.simpleai.util

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Data class representing accelerometer values.
 * x, y, z are in m/s² (standard accelerometer units).
 * On a phone held in portrait:
 *   - x: tilting left/right
 *   - y: tilting forward/back
 *   - z: perpendicular to screen (gravity when flat)
 */
data class AccelerometerData(
    val x: Float = 0f,
    val y: Float = 0f,
    val z: Float = 0f
)

/**
 * Provider for accelerometer data.
 * Platform implementations should start/stop sensor updates.
 */
interface AccelerometerProvider {
    /**
     * Flow of accelerometer data updates.
     */
    val accelerometerData: StateFlow<AccelerometerData>
    
    /**
     * Start listening for accelerometer updates.
     */
    fun start()
    
    /**
     * Stop listening for accelerometer updates.
     */
    fun stop()
}

/**
 * Factory function to create platform-specific accelerometer provider.
 */
expect fun createAccelerometerProvider(): AccelerometerProvider
