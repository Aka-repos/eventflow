package com.app.eventflow.core.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext

/**
 * Efecto de composición reutilizable: registra el acelerómetro mientras la pantalla está activa y
 * dispara [onShake] al detectar una sacudida. SOLO cablea el ciclo de vida del sensor (registro/
 * desregistro atado al DisposableEffect) — la lógica de detección vive en [ShakeDetector] (pura) y
 * la reacción vive en el ViewModel de la pantalla. Sin permisos: el acelerómetro no requiere ninguno.
 */
@Composable
fun ShakeEffect(enabled: Boolean = true, onShake: () -> Unit) {
    val context = LocalContext.current
    val currentOnShake by rememberUpdatedState(onShake)
    val detector = remember { ShakeDetector() }

    DisposableEffect(enabled) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        if (!enabled || sensorManager == null || accelerometer == null) {
            onDispose { }
        } else {
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    val detected = detector.onSample(
                        event.values[0], event.values[1], event.values[2],
                        System.currentTimeMillis(),
                    )
                    if (detected) currentOnShake()
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }
            sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_UI)
            onDispose { sensorManager.unregisterListener(listener) }
        }
    }
}
