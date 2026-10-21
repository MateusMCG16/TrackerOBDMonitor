package br.com.tracker.obd

import br.com.tracker.obd.data.*
import br.com.tracker.obd.obd.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

/** Both PIDs supported, so the session may ask for them in a single CAN message. */
@OptIn(ExperimentalCoroutinesApi::class)
class ObdCombinedSessionTest {
    private fun transport(protocol: String, combined: String) = FakeTransport { when (it) {
        "ATZ" -> "ELM327 v1.5>"
        "0100" -> "410000180000>"   // Velocidade e RPM anunciados.
        "ATDPN" -> protocol
        "010C0D" -> combined
        "010C" -> "410C0AF8>"
        "010D" -> "410D32>"
        else -> "OK>"
    } }

    @Test fun onCanBothSensorsComeFromOneRequestAtFiveHz() = runTest {
        val transport = transport("A6>", "410C1AF80D64>")
        val repo = ObdRepository(backgroundScope, TransportConnector { transport }, MonotonicClock { testScheduler.currentTime })
        repo.connect("fake", "ELM"); runCurrent(); advanceTimeBy(1000)
        assertTrue("010C0D" in transport.writes)
        assertFalse("010C" in transport.writes)
        assertFalse("010D" in transport.writes)
        val telemetry = repo.state.value.telemetry
        assertEquals(1726.0, telemetry.rpm.reading.value)
        assertEquals(100.0, telemetry.speed.reading.value)
        // One transaction per cycle, so each sensor is sampled at the 200 ms floor.
        for (sensor in Sensor.entries)
            assertEquals(5, repo.queryTrace.value.count { it.sensor == sensor && it.valid })
        assertEquals(1, transport.maximumPending)
        repo.close()
    }
    @Test fun aNonCanProtocolKeepsOnePidPerRequest() = runTest {
        val transport = transport("3>", "410C1AF80D64>")
        val repo = ObdRepository(backgroundScope, TransportConnector { transport }, MonotonicClock { testScheduler.currentTime })
        repo.connect("fake", "ELM"); runCurrent(); advanceTimeBy(1000)
        assertFalse("010C0D" in transport.writes)
        assertTrue("010C" in transport.writes && "010D" in transport.writes)
        assertEquals(702.0, repo.state.value.telemetry.rpm.reading.value)
        assertEquals(50.0, repo.state.value.telemetry.speed.reading.value)
        repo.close()
    }
    @Test fun aCloneThatIgnoresTheCombinedRequestFallsBackWithoutReconnecting() = runTest {
        var connections = 0
        val transport = transport("A6>", "NO DATA>")
        val repo = ObdRepository(backgroundScope, TransportConnector { connections++; transport },
            MonotonicClock { testScheduler.currentTime })
        repo.connect("fake", "ELM"); runCurrent(); advanceTimeBy(3000)
        assertTrue("010C" in transport.writes, "Writes: ${transport.writes}")
        assertEquals(702.0, repo.state.value.telemetry.rpm.reading.value)
        assertEquals(50.0, repo.state.value.telemetry.speed.reading.value)
        assertEquals(1, connections) // The Bluetooth session survived the step down.
        assertFalse(transport.closed)
        assertTrue(repo.state.value.warnings.any { "010C0D" in it })
        repo.close()
    }
}
