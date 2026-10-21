package br.com.tracker.obd.demo

import br.com.tracker.obd.data.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.sin

class DemoTelemetrySource(private val clock: MonotonicClock = SystemMonotonicClock) {
    suspend fun run(onSample: (Sensor, Double, Long) -> Unit) {
        val start = clock.nowMs()
        while (currentCoroutineContext().isActive) {
            val now = clock.nowMs()
            val seconds = (now - start) / 1000.0
            val speed = 50 * (1 - kotlin.math.cos(seconds * 2 * PI / 28))
            val gear = when { speed < 22 -> 1; speed < 46 -> 2; speed < 72 -> 3; else -> 4 }
            val rpm = if (speed < 1) 850.0 else (900 + speed * 105 / gear + 40 * sin(seconds)).coerceAtMost(6000.0)
            onSample(Sensor.SPEED, speed, now)
            onSample(Sensor.RPM, rpm, now)
            // Same start-relative rule: no catch-up after suspension.
            delay((now + 200 - clock.nowMs()).coerceAtLeast(1))
        }
    }
}
