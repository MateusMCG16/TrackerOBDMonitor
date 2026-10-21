package br.com.tracker.obd

import br.com.tracker.obd.data.*
import br.com.tracker.obd.obd.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

private val ALTERNATING = listOf(PollTarget.RPM, PollTarget.SPEED)
private fun ok(target: PollTarget, value: Double = 0.0) = target.sensors.map { it to PidResult.Value(value) }
private fun missing(target: PollTarget, reason: String) = target.sensors.map { it to PidResult.Missing(reason) }

@OptIn(ExperimentalCoroutinesApi::class)
class ObdPollingSchedulerTest {
    @Test fun twoSensorsEachHaveFiveHzWithNoOverlap() = runTest {
        val clock = MonotonicClock { testScheduler.currentTime }
        val starts = mutableListOf<Pair<PollTarget, Long>>()
        val samples = mutableListOf<QuerySample>()
        var pending = 0; var maxPending = 0
        val job = launch {
            ObdPollingScheduler(clock).run(ALTERNATING, { target ->
                starts += target to clock.nowMs(); pending++; maxPending = maxOf(maxPending, pending)
                delay(20); pending--; ok(target)
            }, samples::add)
        }
        advanceTimeBy(1000); runCurrent()
        for (target in ALTERNATING) {
            val times = starts.filter { it.first == target && it.second < 1000 }.map { it.second }
            assertEquals(5, times.size)
            assertTrue(times.zipWithNext().all { (a, b) -> b - a == 200L })
        }
        assertEquals(1, maxPending)
        assertTrue(starts.zipWithNext().all { (a,b) -> a.first != b.first })
        assertTrue(samples.all { it.latencyMs == 20L })
        job.cancelAndJoin()
    }
    @Test fun combinedTargetGivesBothSensorsFiveHzInOneTransaction() = runTest {
        val clock = MonotonicClock { testScheduler.currentTime }
        val starts = mutableListOf<Long>()
        val samples = mutableListOf<QuerySample>()
        val job = launch {
            ObdPollingScheduler(clock).run(listOf(PollTarget.BOTH), { target ->
                starts += clock.nowMs(); delay(132) // Latência medida no veículo.
                ok(target)
            }, samples::add)
        }
        advanceTimeBy(1000)
        assertEquals(listOf(0L,200L,400L,600L,800L), starts)
        for (sensor in Sensor.entries)
            assertEquals(5, samples.count { sample -> sample.results.any { it.first == sensor && it.second is PidResult.Value } })
        // Both readings come out of the same message, so they share one timestamp.
        assertTrue(samples.all { it.results.size == 2 })
        job.cancelAndJoin()
    }
    @Test fun slowAdapterReducesFrequencyWithoutQueueOrCatchup() = runTest {
        val clock = MonotonicClock { testScheduler.currentTime }
        val starts = mutableListOf<Long>()
        val samples = mutableListOf<QuerySample>()
        val job = launch {
            ObdPollingScheduler(clock).run(ALTERNATING, { target ->
                starts += clock.nowMs(); delay(240); ok(target, 10.0)
            }, samples::add)
        }
        advanceTimeBy(3000)
        job.cancelAndJoin()
        assertTrue(starts.size in 9..11, "Starts: $starts")
        assertTrue(starts.zipWithNext().all { (a,b) -> b - a >= 286L })
        assertTrue(samples.all { it.slotMs >= 286 })
    }
    @Test fun overdueWorkIsNeverReplayed() = runTest {
        val clock = MonotonicClock { testScheduler.currentTime }
        val starts = mutableListOf<Long>()
        var first = true
        val job = launch {
            ObdPollingScheduler(clock).run(ALTERNATING, { target ->
                starts += clock.nowMs()
                delay(if (first) 1000 else 10); first = false
                ok(target)
            }, {})
        }
        advanceTimeBy(2200); job.cancelAndJoin()
        assertEquals(2, starts.size)
        assertTrue(starts[1] >= 1160)
    }
    @Test fun oneSupportedSensorRemainsAtFiveHz() = runTest {
        val clock = MonotonicClock { testScheduler.currentTime }
        val starts = mutableListOf<Long>()
        val job = launch { ObdPollingScheduler(clock).run(listOf(PollTarget.SPEED), { target ->
            starts += clock.nowMs(); delay(10); ok(target)
        }, {}) }
        advanceTimeBy(1000); job.cancelAndJoin()
        assertEquals(listOf(0L,200L,400L,600L,800L), starts)
    }
    @Test fun noDataBacksOffAndCancellationStopsFurtherRequests() = runTest {
        val clock = MonotonicClock { testScheduler.currentTime }
        val slots = mutableListOf<Long>()
        var requests = 0
        val job = launch { ObdPollingScheduler(clock).run(ALTERNATING, { target ->
            requests++; delay(10); missing(target, "NO DATA")
        }, { slots += it.slotMs }) }
        advanceTimeBy(1000); job.cancelAndJoin()
        val count = requests
        advanceTimeBy(10000)
        assertEquals(count, requests)
        assertTrue(slots.zipWithNext().all { (a,b) -> b > a })
        assertTrue(requests < 10)
    }
    @Test fun combinedReplyWithOneValidSensorIsNotTreatedAsFailure() = runTest {
        val clock = MonotonicClock { testScheduler.currentTime }
        val slots = mutableListOf<Long>()
        val job = launch { ObdPollingScheduler(clock).run(listOf(PollTarget.BOTH), {
            delay(10)
            listOf(Sensor.RPM to PidResult.Value(900.0), Sensor.SPEED to PidResult.Missing("NO DATA"))
        }, { slots += it.slotMs }) }
        advanceTimeBy(1000); job.cancelAndJoin()
        assertTrue(slots.isNotEmpty())
        assertTrue(slots.all { it == 200L }, "Slots: $slots") // No back-off: the message did carry data.
    }
    @Test fun frequencyRecoversGraduallyAfterSlowResponse() = runTest {
        val clock = MonotonicClock { testScheduler.currentTime }
        val slots = mutableListOf<Long>()
        var slow = true
        val job = launch { ObdPollingScheduler(clock).run(ALTERNATING, { target ->
            delay(if (slow) 300 else 10); slow = false; ok(target)
        }, { slots += it.slotMs }) }
        advanceTimeBy(2000); job.cancelAndJoin()
        assertTrue(slots.size >= 4)
        assertTrue(slots.zipWithNext().all { (a,b) -> a - b == 5L })
        assertTrue(slots.all { it >= 100 })
    }
}
