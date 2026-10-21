package br.com.tracker.obd.obd

import br.com.tracker.obd.data.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.ceil
import kotlin.math.max

data class QuerySample(val sensor: Sensor, val result: PidResult, val startedMs: Long, val finishedMs: Long, val slotMs: Long) {
    val latencyMs get() = finishedMs - startedMs
}

/** One worker, no producer, no queue, monotonic start-to-start deadlines. */
class ObdPollingScheduler(private val clock: MonotonicClock = SystemMonotonicClock) {
    suspend fun run(
        supported: Set<Sensor>, query: suspend (Sensor) -> PidResult,
        onSample: (QuerySample) -> Unit
    ) {
        require(supported.isNotEmpty())
        val sensors = Sensor.entries.filter { it in supported }
        val minimumSlot = if (sensors.size == 2) 100L else 200L
        var slot = minimumSlot
        var index = 0
        var nextStart = clock.nowMs()
        while (currentCoroutineContext().isActive) {
            delay((nextStart - clock.nowMs()).coerceAtLeast(0))
            val started = clock.nowMs()
            val sensor = sensors[index]
            val result = query(sensor) // Must finish through '>' before another request.
            val finished = clock.nowMs()
            val latency = (finished - started).coerceAtLeast(0)
            val needed = ceil(latency * 1.15).toLong() + 10
            // Fast slowdown, gradual recovery. A failed response also backs off.
            slot = if (result is PidResult.Missing) max(slot, max(needed, slot * 3 / 2)).coerceAtMost(5000)
                else if (needed > slot) needed.coerceAtMost(5000)
                else max(minimumSlot, max(needed, slot - 5))
            onSample(QuerySample(sensor, result, started, finished, slot))
            index = (index + 1) % sensors.size
            // Always schedule from the actual start; missed deadlines are discarded.
            nextStart = max(started + slot, finished + 10)
        }
    }
}
