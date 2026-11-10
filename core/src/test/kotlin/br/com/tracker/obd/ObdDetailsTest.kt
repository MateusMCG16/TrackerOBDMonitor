package br.com.tracker.obd

import br.com.tracker.obd.data.*
import br.com.tracker.obd.obd.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

/**
 * The detail screens. The replies below are what the vehicle answered through the
 * v2.1 clone on 2026-11-09, in the form the adapter prints them with headers off:
 * data bytes only, padded with AA to the end of the CAN frame.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ObdDetailsTest {
    private fun value(raw: String, signal: Signal) = ExtraPids.readings(raw).getValue(signal)

    @Test fun threePidsInOneFrame() {
        val parsed = ExtraPids.readings("41043511360E7B\r>")
        assertEquals(0x35 * 100.0 / 255, parsed.getValue(Signal.ENGINE_LOAD), 1e-9)
        assertEquals(0x36 * 100.0 / 255, parsed.getValue(Signal.THROTTLE), 1e-9)
        assertEquals(-2.5, parsed.getValue(Signal.TIMING_ADVANCE), 1e-9)
    }
    @Test fun framePaddingIsNotAPid() {
        // AA fills the frame after the last PID; it must end the walk, not be decoded.
        val parsed = ExtraPids.readings("411000E54932AA\r>")
        assertEquals(setOf(Signal.AIR_FLOW, Signal.PEDAL_D), parsed.keys)
        assertEquals(2.29, parsed.getValue(Signal.AIR_FLOW), 1e-9)
    }
    @Test fun scalesMatchTheStandard() {
        assertEquals(89.0, value("4105810F694646>", Signal.COOLANT))
        assertEquals(65.0, value("4105810F694646>", Signal.INTAKE_AIR))
        assertEquals(30.0, value("4105810F694646>", Signal.AMBIENT))
        assertEquals(383.0, value("413C10862F8DAA>", Signal.CATALYST), 1e-9)
        assertEquals(1.0, value("41448000AAAAAA>", Signal.LAMBDA), 1e-9)
        assertEquals(-1.5625, value("41067E077F4C2B>", Signal.FUEL_TRIM_SHORT), 1e-9)
        assertEquals(360.0, value("41335D0A782E00>", Signal.FUEL_PRESSURE))
        assertEquals(0.73, value("4114927D158CFF>", Signal.O2_UPSTREAM), 1e-9)
        assertEquals(1596.0, value("411F063C030200>", Signal.RUN_TIME))
        assertEquals(2.0, value("411F063C030200>", Signal.FUEL_SYSTEM))
        assertEquals(16939.0, value("4131422B210000>", Signal.DISTANCE_SINCE_CLEAR))
        assertEquals(190.0, value("41473A4A1930BE>", Signal.WARMUPS))
        assertEquals(1365 * 0.03125, value("41870105550000>", Signal.MANIFOLD_PRESSURE), 1e-9)
    }
    @Test fun aLongReplyIsReassembled() {
        // PID 68 carries seven bytes, so it arrives as a length line plus two frames.
        assertEquals(70.0, value("009\r0:416807696E65\r1:282828AAAAAAAA\r>", Signal.INTAKE_AIR_2))
    }
    @Test fun theModuleThatAnswersMoreIsTheEngine() {
        // Both modules answer PID 42; only the engine also knows the ethanol content.
        for (raw in listOf("41423627525CAA\r4142351EAAAAAA\r>", "4142351EAAAAAA\r41423627525CAA\r>")) {
            val parsed = ExtraPids.readings(raw)
            assertEquals(13.863, parsed.getValue(Signal.ECM_VOLTAGE), 1e-9, raw)
            assertEquals(13.598, parsed.getValue(Signal.TCM_VOLTAGE), 1e-9, raw)
            assertEquals(0x5C * 100.0 / 255, parsed.getValue(Signal.ETHANOL), 1e-9, raw)
        }
    }
    @Test fun errorsAndGarbageYieldNothing() {
        for (raw in listOf("NO DATA>", "?>", "CAN ERROR>", "OK>", "41>", "4104>"))
            assertTrue(ExtraPids.readings(raw).isEmpty(), raw)
    }
    @Test fun supportMaskAddsUpTheModules() {
        val pids = ExtraPids.mask("4100BE5FB813AA\r410080000001AA\r>", 0x00)
        assertTrue(setOf(0x01, 0x04, 0x0C, 0x0D, 0x11, 0x20).all { it in pids })
        assertFalse(0x02 in pids)
        assertEquals(setOf(0x21, 0x2E, 0x2F, 0x30, 0x31, 0x33, 0x3C, 0x40), ExtraPids.mask("41208007A011AA>", 0x20))
        assertTrue(ExtraPids.mask("NO DATA>", 0x20).isEmpty())
    }
    @Test fun vinNeedsAllSeventeenCharacters() {
        val vin = "9BGTEST0000000001"
        val hex = "490201" + vin.map { "%02X".format(it.code) }.joinToString("")
        val raw = "014\r0:${hex.take(12)}\r1:${hex.substring(12, 26)}\r2:${hex.substring(26)}\r>"
        assertEquals(vin, ExtraPids.vin(raw))
        assertNull(ExtraPids.vin("014\r0:${hex.take(12)}\r1:${hex.substring(12, 26)}\r>"))
        assertNull(ExtraPids.vin("NO DATA>"))
    }
    @Test fun adapterVoltageAndProtocolName() {
        assertEquals(13.5, ExtraPids.adapterVoltage("13.5V\r>"))
        assertNull(ExtraPids.adapterVoltage("?>"))
        assertEquals("CAN 29 bits, 500 kbps", ExtraPids.protocolName("A7\r>"))
        assertEquals("CAN 11 bits, 500 kbps", ExtraPids.protocolName("6>"))
        assertEquals("SAE J1939", ExtraPids.protocolName("A>"))
    }
    @Test fun everyRequestStaysInsideWhatTheCloneSurvives() {
        // Measured on the vehicle: a 4-PID request resets the adapter; up to 3 PIDs whose
        // reply fits one CAN frame (six data bytes after the 41) are safe.
        for (query in ExtraQuery.entries) {
            assertTrue(query.pids.size <= 3, query.name)
            val payload = query.pids.sumOf { 1 + ExtraPids.specs.getValue(it).width }
            if (query.pids.size > 1) assertTrue(payload <= 6, "${query.name}: $payload bytes")
        }
    }

    private fun car(protocol: String = "A7>") = FakeTransport { when (it) {
        "ATZ" -> "ELM327 v2.1>"
        "0100" -> "4100BE5FB813AA\r410080000001AA>"
        "0120" -> "41208007A011AA>"; "0140" -> "4140FED0C001AA>"
        "0160" -> "416003000001AA>"; "0180" -> "418002002000AA>"
        "ATDPN" -> protocol
        "010C0D" -> "410C0E0E0D00AA>"
        "010C" -> "410C0E0E>"; "010D" -> "410D00>"
        "0104110E" -> "41043511360E7B>"; "011049" -> "411000E54932AA>"
        "0106074C" -> "41067E077F4C2B>"; "014345" -> "4143003F451FAA>"
        "01474A30" -> "41473A4A1930BE>"; "011F03" -> "411F063C030200>"
        "013121" -> "4131422B210000>"; "ATRV" -> "13.5V>"
        "0101" -> "410181000000>"; "03" -> "43010133>"; "07" -> "470201330442>"; "0A" -> "4A00>"
        else -> if (it.startsWith("AT")) "OK>" else "NO DATA>"
    } }

    @Test fun withNoDetailScreenOpenOnlySpeedAndRpmAreAsked() = runTest {
        val transport = car()
        val repo = ObdRepository(backgroundScope, TransportConnector { transport }, MonotonicClock { testScheduler.currentTime })
        repo.connect("fake", "ELM"); runCurrent(); advanceTimeBy(3000)
        assertEquals(setOf("ATZ", "ATE0", "ATL0", "ATS0", "ATH0", "ATSP0", "0100", "ATDPN", "010C0D"), transport.writes.toSet())
        assertTrue(repo.state.value.extras.readings.isEmpty())
        assertEquals(2, repo.state.value.vehicle.modules)
        assertEquals("ELM327 v2.1", repo.state.value.vehicle.adapter)
        repo.close()
    }
    @Test fun theOpenScreenDecidesWhatIsRead() = runTest {
        val transport = car()
        val repo = ObdRepository(backgroundScope, TransportConnector { transport }, MonotonicClock { testScheduler.currentTime })
        repo.focus(setOf(DataGroup.MOTOR))
        repo.connect("fake", "ELM"); runCurrent(); advanceTimeBy(5000)
        val state = repo.state.value
        assertEquals(0x35 * 100.0 / 255, state.extras.reading(Signal.ENGINE_LOAD).value!!, 1e-9)
        assertEquals(1596.0, state.extras.reading(Signal.RUN_TIME).value)
        assertEquals("CAN 29 bits, 500 kbps", state.vehicle.protocol)
        // Motor needs neither the manifold pressure nor the distances.
        assertFalse("0187" in transport.writes)
        assertFalse("013121" in transport.writes)
        // Speed and RPM keep arriving, and no request ever exceeds three PIDs.
        assertEquals(0x0E0E / 4.0, state.telemetry.rpm.reading.value)
        assertTrue(transport.writes.filter { it.startsWith("01") }.all { it.length <= 8 })
        assertEquals(1, transport.maximumPending)

        repo.focus(setOf(DataGroup.HISTORY)); advanceTimeBy(3000)
        assertEquals(16939.0, repo.state.value.extras.reading(Signal.DISTANCE_SINCE_CLEAR).value)
        repo.close()
    }
    @Test fun unsupportedSignalsAreNamedAndNeverRequested() = runTest {
        val transport = car()
        val repo = ObdRepository(backgroundScope, TransportConnector { transport }, MonotonicClock { testScheduler.currentTime })
        repo.focus(setOf(DataGroup.MOTOR))
        repo.connect("fake", "ELM"); runCurrent(); advanceTimeBy(3000)
        // The masks above announce every PID this app reads.
        assertTrue(repo.state.value.extras.unsupported.isEmpty(), "${repo.state.value.extras.unsupported}")
        repo.close()
    }
    @Test fun aNonCanProtocolLeavesTheDetailScreensEmptyAndSaysWhy() = runTest {
        val transport = car("3>")
        val repo = ObdRepository(backgroundScope, TransportConnector { transport }, MonotonicClock { testScheduler.currentTime })
        repo.focus(setOf(DataGroup.MOTOR))
        repo.connect("fake", "ELM"); runCurrent(); advanceTimeBy(3000)
        assertFalse("0104110E" in transport.writes)
        assertNotNull(repo.state.value.extras.notice)
        assertEquals("ISO 9141-2", repo.state.value.vehicle.protocol)
        repo.close()
    }
    @Test fun theThreeCodeListsAreReadAndLabelled() = runTest {
        val transport = car()
        val repo = ObdRepository(backgroundScope, TransportConnector { transport }, MonotonicClock { testScheduler.currentTime })
        repo.connect("fake", "ELM"); runCurrent(); advanceTimeBy(1000)
        repo.requestDtcRead(); advanceTimeBy(1000)
        val report = repo.state.value.dtc
        assertEquals(DtcPhase.READY, report.phase)
        // P0133 is in both lists: it is shown once, as confirmed.
        assertEquals(listOf("P0133" to DtcKind.STORED, "P0442" to DtcKind.PENDING), report.codes.map { it.code to it.kind })
        assertTrue(listOf("03", "07", "0A").all { it in transport.writes })
        repo.close()
    }
    @Test fun demoFillsEveryDetailValue() = runTest {
        val repo = ObdRepository(backgroundScope, TransportConnector { error("Unexpected Bluetooth") }, MonotonicClock { testScheduler.currentTime })
        repo.demo(); runCurrent(); advanceTimeBy(600)
        assertEquals(Signal.entries.toSet(), repo.state.value.extras.readings.keys)
        repo.close()
    }
}
