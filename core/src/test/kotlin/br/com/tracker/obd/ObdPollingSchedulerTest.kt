package br.com.tracker.obd

import br.com.tracker.obd.data.*
import br.com.tracker.obd.obd.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class ObdPollingSchedulerTest {
    @Test fun twoSensorsEachHaveFiveHzWithNoOverlap() = runTest {
        val clock = MonotonicClock { testScheduler.currentTime }
        val starts = mutableListOf<Pair<Sensor, Long>>()
        val samples = mutableListOf<QuerySample>()
        var pending = 0; var maxPending = 0
        val job = launch {
            ObdPollingScheduler(clock).run(Sensor.entries.toSet(), { sensor ->
                starts += sensor to clock.nowMs(); pending++; maxPending = maxOf(maxPending, pending)
                delay(20); pending--; PidResult.Value(0.0)
            }, samples::add)
        }
        advanceTimeBy(1000); runCurrent()
        for (sensor in Sensor.entries) {
            val times = starts.filter { it.first == sensor && it.second < 1000 }.map { it.second }
            assertEquals(5, times.size)
            assertTrue(times.zipWithNext().all { (a, b) -> b - a == 200L })
        }
        assertEquals(1, maxPending)
        assertTrue(starts.zipWithNext().all { (a,b) -> a.first != b.first })
        assertTrue(samples.all { it.latencyMs == 20L })
        job.cancelAndJoin()
    }
    @Test fun slowAdapterReducesFrequencyWithoutQueueOrCatchup() = runTest {
        val clock = MonotonicClock { testScheduler.currentTime }
        val starts = mutableListOf<Long>()
        val samples = mutableListOf<QuerySample>()
        val job = launch {
            ObdPollingScheduler(clock).run(Sensor.entries.toSet(), {
                starts += clock.nowMs(); delay(240); PidResult.Value(10.0)
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
            ObdPollingScheduler(clock).run(Sensor.entries.toSet(), {
                starts += clock.nowMs()
                delay(if (first) 1000 else 10); first = false
                PidResult.Value(0.0)
            }, {})
        }
        advanceTimeBy(2200); job.cancelAndJoin()
        assertEquals(2, starts.size)
        assertTrue(starts[1] >= 1160)
    }
    @Test fun oneSupportedSensorRemainsAtFiveHz() = runTest {
        val clock = MonotonicClock { testScheduler.currentTime }
        val starts = mutableListOf<Long>()
        val job = launch { ObdPollingScheduler(clock).run(setOf(Sensor.SPEED), {
            starts += clock.nowMs(); delay(10); PidResult.Value(0.0)
        }, {}) }
        advanceTimeBy(1000); job.cancelAndJoin()
        assertEquals(listOf(0L,200L,400L,600L,800L), starts)
    }
    @Test fun noDataBacksOffAndCancellationStopsFurtherRequests() = runTest {
        val clock = MonotonicClock { testScheduler.currentTime }
        val slots = mutableListOf<Long>()
        var requests = 0
        val job = launch { ObdPollingScheduler(clock).run(Sensor.entries.toSet(), {
            requests++; delay(10); PidResult.Missing("NO DATA")
        }, { slots += it.slotMs }) }
        advanceTimeBy(1000); job.cancelAndJoin()
        val count = requests
        advanceTimeBy(10000)
        assertEquals(count, requests)
        assertTrue(slots.zipWithNext().all { (a,b) -> b > a })
        assertTrue(requests < 10)
    }
    @Test fun frequencyRecoversGraduallyAfterSlowResponse() = runTest {
        val clock = MonotonicClock { testScheduler.currentTime }
        val slots = mutableListOf<Long>()
        var slow = true
        val job = launch { ObdPollingScheduler(clock).run(Sensor.entries.toSet(), {
            delay(if (slow) 300 else 10); slow = false; PidResult.Value(0.0)
        }, { slots += it.slotMs }) }
        advanceTimeBy(2000); job.cancelAndJoin()
        assertTrue(slots.size >= 4)
        assertTrue(slots.zipWithNext().all { (a,b) -> a - b == 5L })
        assertTrue(slots.all { it >= 100 })
    }
}
