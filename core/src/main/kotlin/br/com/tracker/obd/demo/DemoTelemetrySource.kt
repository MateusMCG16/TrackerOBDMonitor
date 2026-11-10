package br.com.tracker.obd.demo

import br.com.tracker.obd.data.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin

class DemoTelemetrySource(private val clock: MonotonicClock = SystemMonotonicClock) {
    suspend fun run(
        onExtras: (Map<Signal, Double>, Long) -> Unit = { _, _ -> },
        onSample: (Sensor, Double, Long) -> Unit
    ) {
        val start = clock.nowMs()
        while (currentCoroutineContext().isActive) {
            val now = clock.nowMs()
            val seconds = (now - start) / 1000.0
            val speed = 50 * (1 - kotlin.math.cos(seconds * 2 * PI / 28))
            val gear = when { speed < 22 -> 1; speed < 46 -> 2; speed < 72 -> 3; else -> 4 }
            val rpm = if (speed < 1) 850.0 else (900 + speed * 105 / gear + 40 * sin(seconds)).coerceAtMost(6000.0)
            onSample(Sensor.SPEED, speed, now)
            onSample(Sensor.RPM, rpm, now)
            onExtras(extras(seconds, speed, rpm), now)
            // Same start-relative rule: no catch-up after suspension.
            delay((now + 200 - clock.nowMs()).coerceAtLeast(1))
        }
    }

    /**
     * Plausible companions for the simulated speed and RPM, anchored on what the
     * real vehicle reported at idle. [effort] is 0 while coasting and 1 at the
     * steepest part of the acceleration.
     */
    internal fun extras(seconds: Double, speed: Double, rpm: Double): Map<Signal, Double> {
        val effort = max(0.0, sin(seconds * 2 * PI / 28))
        val throttle = 16 + 26 * effort
        return mapOf(
            Signal.ENGINE_LOAD to 22 + 45 * effort,
            Signal.ABSOLUTE_LOAD to 25 + 40 * effort,
            Signal.TIMING_ADVANCE to if (speed < 1) -1.5 else 8 + 14 * (1 - effort),
            Signal.THROTTLE to throttle,
            Signal.THROTTLE_COMMANDED to throttle,
            Signal.THROTTLE_RELATIVE to throttle - 10,
            Signal.PEDAL_D to 19.6 + 32 * effort,
            Signal.PEDAL_E to 9.8 + 16 * effort,
            Signal.RUN_TIME to 283 + seconds,
            Signal.MANIFOLD_PRESSURE to 42 + 48 * effort,
            Signal.BAROMETRIC to 93.0,
            Signal.AIR_FLOW to rpm / 900 * 2.2 * (1 + 2 * effort),
            Signal.FUEL_TRIM_SHORT to 2.5 * sin(seconds * 1.7),
            Signal.FUEL_TRIM_LONG to -0.8,
            Signal.LAMBDA to 1.0,
            Signal.FUEL_PRESSURE to 267 + 90 * effort,
            Signal.FUEL_LEVEL to 55.0,
            Signal.ETHANOL to 36.0,
            Signal.PURGE to 12 * effort,
            Signal.FUEL_SYSTEM to 2.0,
            Signal.COOLANT to 91 + 2 * sin(seconds / 9),
            Signal.INTAKE_AIR to 51.0,
            Signal.INTAKE_AIR_2 to 60.0,
            Signal.AMBIENT to 29.0,
            Signal.CATALYST to 365 + 120 * effort,
            Signal.O2_UPSTREAM to 0.45 + 0.35 * sin(seconds * 4),
            Signal.O2_DOWNSTREAM to 0.66 + 0.04 * sin(seconds / 3),
            Signal.ECM_VOLTAGE to 13.84,
            Signal.TCM_VOLTAGE to 13.58,
            Signal.ADAPTER_VOLTAGE to 13.3,
            Signal.DISTANCE_SINCE_CLEAR to 16939.0,
            Signal.WARMUPS to 190.0,
            Signal.DISTANCE_WITH_MIL to 0.0
        )
    }
}
