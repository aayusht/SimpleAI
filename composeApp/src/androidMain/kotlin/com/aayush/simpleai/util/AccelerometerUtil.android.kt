package com.aayush.simpleai.util

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class AndroidAccelerometerProvider : AccelerometerProvider, KoinComponent, SensorEventListener {
    private val context: Context by inject()
    
    private val sensorManager: SensorManager by lazy {
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }
    
    private val accelerometer: Sensor? by lazy {
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    }
    
    private val _accelerometerData = MutableStateFlow(AccelerometerData())
    override val accelerometerData: StateFlow<AccelerometerData> = _accelerometerData.asStateFlow()
    
    override fun start() {
        accelerometer?.let { sensor ->
            sensorManager.registerListener(
                this,
                sensor,
                SensorManager.SENSOR_DELAY_GAME
            )
        }
    }
    
    override fun stop() {
        sensorManager.unregisterListener(this)
    }
    
    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            if (it.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                _accelerometerData.value = AccelerometerData(
                    x = it.values[0],
                    y = it.values[1],
                    z = it.values[2]
                )
            }
        }
    }
    
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not needed for this implementation
    }
}

actual fun createAccelerometerProvider(): AccelerometerProvider {
    return AndroidAccelerometerProvider()
}
