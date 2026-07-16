package com.app.eventflow.core.sensor

import kotlin.math.sqrt

/**
 * Lógica PURA de detección de sacudida (sin dependencias de Android → unit-testeable en JVM).
 * Recibe muestras del acelerómetro y decide si constituyen una sacudida, con:
 *  - umbral en g (fuerza sobre la gravedad terrestre), y
 *  - debounce temporal para evitar disparos consecutivos.
 *
 * Reutilizable: cualquier pantalla puede alimentar sus muestras y reaccionar al callback.
 */
class ShakeDetector(
    private val gThreshold: Float = DEFAULT_G_THRESHOLD,
    private val debounceMillis: Long = DEFAULT_DEBOUNCE_MILLIS,
) {

    private var lastShakeAt: Long? = null

    /**
     * @return true SOLO cuando esta muestra supera el umbral y ya pasó el debounce desde la última
     * sacudida aceptada. La primera sacudida nunca se suprime. El estado interno se actualiza en ese caso.
     */
    fun onSample(x: Float, y: Float, z: Float, nowMillis: Long): Boolean {
        val gForce = sqrt(x * x + y * y + z * z) / GRAVITY_EARTH
        if (gForce < gThreshold) return false
        val previous = lastShakeAt
        if (previous != null && nowMillis - previous < debounceMillis) return false
        lastShakeAt = nowMillis
        return true
    }

    companion object {
        /** Igual a android.hardware.SensorManager.GRAVITY_EARTH (evita depender de Android aquí). */
        const val GRAVITY_EARTH = 9.80665f

        /** ~2.7 g: sacudida deliberada, no un movimiento normal al caminar. */
        const val DEFAULT_G_THRESHOLD = 1.3f

        /** Ignora sacudidas dentro de 1 s de la anterior (evita llamadas repetidas). */
        const val DEFAULT_DEBOUNCE_MILLIS = 1_000L
    }
}
