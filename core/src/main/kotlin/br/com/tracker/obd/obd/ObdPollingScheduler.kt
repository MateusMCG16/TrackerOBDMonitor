package br.com.tracker.obd.obd

import br.com.tracker.obd.data.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.ceil
import kotlin.math.max

data class QuerySample(
    val target: PollTarget, val results: List<Pair<Sensor, PidResult>>,
    val startedMs: Long, val finishedMs: Long, val slotMs: Long
) {
    val latencyMs get() = finishedMs - startedMs
}

/** One worker, no producer, no queue, monotonic start-to-start deadlines. */
class ObdPollingScheduler(private val clock: MonotonicClock = SystemMonotonicClock) {
    suspend fun run(
        targets: List<PollTarget>, query: suspend (PollTarget) -> List<Pair<Sensor, PidResult>>,
        onSample: (QuerySample) -> Unit
    ) {
        require(targets.isNotEmpty())
        // Two alternating requests share the budget; a single one carries a whole cycle.
        val minimumSlot = if (targets.size == 2) 100L else 200L
        var slot = minimumSlot
        var index = 0
        var nextStart = clock.nowMs()
        while (currentCoroutineContext().isActive) {
            delay((nextStart - clock.nowMs()).coerceAtLeast(0))
            val started = clock.nowMs()
            val target = targets[index]
            val results = query(target) // Must finish through '>' before another request.
            val finished = clock.nowMs()
            val latency = (finished - started).coerceAtLeast(0)
            val needed = ceil(latency * 1.15).toLong() + 10
            val failed = results.none { it.second is PidResult.Value }
            // Fast slowdown, gradual recovery. A failed response also backs off.
            slot = if (failed) max(slot, max(needed, slot * 3 / 2)).coerceAtMost(5000)
                else if (needed > slot) needed.coerceAtMost(5000)
                else max(minimumSlot, max(needed, slot - 5))
            onSample(QuerySample(target, results, started, finished, slot))
            index = (index + 1) % targets.size
            // Always schedule from the actual start; missed deadlines are discarded.
            nextStart = max(started + slot, finished + 10)
        }
    }
}
