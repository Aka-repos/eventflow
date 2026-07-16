package com.app.eventflow.core.sensor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Lógica pura de detección de sacudida: umbral en g + debounce temporal. */
class ShakeDetectorTest {

    private val g = ShakeDetector.GRAVITY_EARTH

    @Test
    fun `still device does not trigger`() {
        val detector = ShakeDetector()
        // en reposo el acelerómetro marca ~1 g en un eje → gForce ≈ 1, bajo el umbral 2.7
        assertFalse(detector.onSample(0f, g, 0f, nowMillis = 0L))
    }

    @Test
    fun `strong jerk above threshold triggers`() {
        val detector = ShakeDetector()
        // ~3.5 g en un eje → supera el umbral
        assertTrue(detector.onSample(3.5f * g, 0f, 0f, nowMillis = 0L))
    }

    @Test
    fun `second shake within debounce window is ignored`() {
        val detector = ShakeDetector(debounceMillis = 1_000L)
        assertTrue(detector.onSample(4f * g, 0f, 0f, nowMillis = 0L))
        // 500 ms después, aún dentro del debounce → ignorada aunque supere el umbral
        assertFalse(detector.onSample(4f * g, 0f, 0f, nowMillis = 500L))
    }

    @Test
    fun `shake after debounce window triggers again`() {
        val detector = ShakeDetector(debounceMillis = 1_000L)
        assertTrue(detector.onSample(4f * g, 0f, 0f, nowMillis = 0L))
        // 1200 ms después, pasado el debounce → vuelve a disparar
        assertTrue(detector.onSample(4f * g, 0f, 0f, nowMillis = 1_200L))
    }

    @Test
    fun `custom threshold is respected`() {
        val detector = ShakeDetector(gThreshold = 2f)
        assertFalse(detector.onSample(1.5f * g, 0f, 0f, nowMillis = 0L)) // bajo el umbral custom
        assertTrue(detector.onSample(3f * g, 0f, 0f, nowMillis = 10L))   // sobre el umbral custom
    }
}
