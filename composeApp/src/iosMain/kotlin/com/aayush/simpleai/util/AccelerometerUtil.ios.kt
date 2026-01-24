package com.aayush.simpleai.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.CoreMotion.CMMotionManager
import platform.Foundation.NSOperationQueue

class IosAccelerometerProvider : AccelerometerProvider {
    private val motionManager = CMMotionManager()
    
    private val _accelerometerData = MutableStateFlow(AccelerometerData())
    override val accelerometerData: StateFlow<AccelerometerData> = _accelerometerData.asStateFlow()
    
    override fun start() {
        if (motionManager.accelerometerAvailable) {
            motionManager.accelerometerUpdateInterval = 1.0 / 60.0 // 60 Hz
            motionManager.startAccelerometerUpdatesToQueue(
                NSOperationQueue.mainQueue
            ) { data, _ ->
                data?.let {
                    // iOS accelerometer values are in G's (1G = 9.8 m/s²)
                    // Convert to m/s² for consistency with Android
                    // Also, iOS has different axis orientation - adjust to match Android
                    _accelerometerData.value = AccelerometerData(
                        x = (it.acceleration.x * -9.8).toFloat(),
                        y = (it.acceleration.y * -9.8).toFloat(),
                        z = (it.acceleration.z * -9.8).toFloat()
                    )
                }
            }
        }
    }
    
    override fun stop() {
        motionManager.stopAccelerometerUpdates()
    }
}

actual fun createAccelerometerProvider(): AccelerometerProvider {
    return IosAccelerometerProvider()
}
