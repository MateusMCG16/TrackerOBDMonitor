package br.com.tracker.obd

import br.com.tracker.obd.data.*
import br.com.tracker.obd.demo.DemoTelemetrySource
import br.com.tracker.obd.obd.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class TelemetryTest {
    @Test fun zeroValidMissingAndStaleAreDistinct() {
        assertEquals(Availability.UNAVAILABLE, Reading().availability(100))
        val zero = Reading(0.0, 0)
        assertEquals(Availability.VALID, zero.availability(100))
        assertEquals(Availability.STALE, zero.availability(1500))
        assertEquals(0.0, zero.copy(issue = "NO DATA").value)
    }
    @Test fun freshnessThresholdAccountsForAdaptiveInterval() {
        assertEquals(1500L, VehicleTelemetry(slotMs = 100).staleAfterMs)
        assertEquals(5000L, VehicleTelemetry(slotMs = 1000).staleAfterMs)
    }
    @Test fun measuredHzDecaysAndStopsWhenResponsesStop() {
        val meter = FrequencyMeter()
        (0L..1800L step 200).forEach(meter::sample)
        assertEquals(5.0, meter.hz(1800), 0.001)
        assertTrue(meter.hz(2500) < 5)
        assertEquals(0.0, meter.hz(4000))
    }
    @Test fun demoGeneratesFiveSamplesEachPerSecondAndReaches100() = runTest {
        val clock = MonotonicClock { testScheduler.currentTime }
        val values = mutableListOf<Triple<Sensor, Double, Long>>()
        val job = launch { DemoTelemetrySource(clock).run { s, v, t -> values += Triple(s,v,t) } }
        advanceTimeBy(1000)
        Sensor.entries.forEach { s -> assertEquals(5, values.count { it.first == s }) }
        advanceTimeBy(13200)
        assertEquals(100.0, values.filter { it.first == Sensor.SPEED }.maxOf { it.second }, 0.01)
        assertTrue(values.filter { it.first == Sensor.RPM }.all { it.second in 800.0..6000.0 })
        job.cancelAndJoin()
    }
    @Test fun repositoryChecksSupportAndOnlyPollsSupportedPid() = runTest {
        val transport = FakeTransport { when(it) {
            "ATZ" -> "ELM327>"; "0100" -> "410000080000>"; "010D" -> "410D00>"; else -> "OK>"
        } }
        val repo = ObdRepository(backgroundScope, TransportConnector { transport }, MonotonicClock { testScheduler.currentTime })
        repo.connect("fake", "Test ELM")
        runCurrent(); advanceTimeBy(1000)
        assertFalse("010C" in transport.writes)
        assertTrue(transport.writes.indexOf("0100") < transport.writes.indexOf("010D"))
        assertEquals(0.0, repo.state.value.telemetry.speed.reading.value)
        assertEquals(Availability.UNAVAILABLE, repo.state.value.telemetry.rpm.reading.availability(1000))
        assertEquals(1, transport.maximumPending)
        repo.disconnect(); runCurrent(); assertTrue(transport.closed)
    }
    @Test fun repeatedMissingReadingsRetainLastMeasurementAndBecomeStale() = runTest {
        var valid = true
        val transport = FakeTransport { when(it) {
            "ATZ" -> "ELM327>"; "0100" -> "410000080000>"
            "010D" -> if (valid) { valid = false; "410D00>" } else "NO DATA>"
            else -> "OK>"
        } }
        val repo = ObdRepository(backgroundScope, TransportConnector { transport }, MonotonicClock { testScheduler.currentTime })
        repo.connect("fake", "ELM"); runCurrent(); advanceTimeBy(14000)
        val t = repo.state.value.telemetry
        assertEquals(0.0, t.speed.reading.value)
        assertEquals(0L, t.speed.reading.measuredAtMs)
        assertEquals(Availability.STALE, t.speed.reading.availability(t.nowMs, t.staleAfterMs))
        assertEquals(0.0, t.speed.hz)
        repo.close()
    }
    @Test fun demoNeverTouchesBluetoothAndSwitchingCancelsIt() = runTest {
        var connections = 0
        val repo = ObdRepository(backgroundScope, TransportConnector { connections++; error("Unexpected Bluetooth") }, MonotonicClock { testScheduler.currentTime })
        repo.demo(); runCurrent(); advanceTimeBy(800)
        assertEquals(ConnectionPhase.DEMO, repo.state.value.phase)
        assertEquals(0, connections)
        assertNotNull(repo.state.value.telemetry.speed.reading.value)
        advanceTimeBy(15000)
        assertEquals(120, repo.queryTrace.value.size)
        repo.disconnect(); runCurrent()
        assertEquals(ConnectionPhase.DISCONNECTED, repo.state.value.phase)
        assertNull(repo.state.value.telemetry.speed.reading.value)
    }
    @Test fun reconnectIsBoundedAndCancelable() = runTest {
        var connections = 0
        val repo = ObdRepository(backgroundScope, TransportConnector { connections++; throw java.io.IOException("link lost") }, MonotonicClock { testScheduler.currentTime })
        repo.connect("fake", "ELM"); runCurrent(); advanceTimeBy(8000); runCurrent()
        assertEquals(4, connections)
        assertEquals(ConnectionPhase.ERROR, repo.state.value.phase)
        repo.connect("fake", "ELM"); runCurrent(); repo.disconnect(); advanceTimeBy(8000)
        assertEquals(5, connections)
    }
    @Test fun backingOffNeverMakesAnOldMeasurementFreshAgain() = runTest {
        var valid = true
        val transport = FakeTransport { when(it) {
            "ATZ" -> "ELM327>"; "0100" -> "410000080000>"
            "010D" -> if (valid) { valid = false; "410D32>" } else "NO DATA>"
            else -> "OK>"
        } }
        val repo = ObdRepository(backgroundScope, TransportConnector { transport }, MonotonicClock { testScheduler.currentTime })
        repo.connect("fake", "ELM"); runCurrent()
        advanceTimeBy(1600)
        assertEquals(Availability.STALE, repo.state.value.telemetry.speed.reading.availability(1600))
        advanceTimeBy(800)
        assertTrue(repo.state.value.telemetry.slotMs > 200)
        assertEquals(Availability.STALE, repo.state.value.telemetry.speed.reading.availability(2400))
        assertEquals(0L, repo.state.value.telemetry.speed.reading.measuredAtMs)
        repo.close()
    }
}
