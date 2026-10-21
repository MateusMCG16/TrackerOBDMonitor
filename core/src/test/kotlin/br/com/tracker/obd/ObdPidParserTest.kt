package br.com.tracker.obd

import br.com.tracker.obd.data.*
import br.com.tracker.obd.obd.*
import kotlin.test.*

class ObdPidParserTest {
    @Test fun parsesSpacedAndCompactRpm() {
        assertEquals(PidResult.Value(1726.0), ObdPidParser.parse(Sensor.RPM, "41 0C 1A F8\r>"))
        assertEquals(PidResult.Value(1726.0), ObdPidParser.parse(Sensor.RPM, "010C\rSEARCHING...\r410C1AF8\r>"))
    }
    @Test fun parsesSpeedAndZero() {
        assertEquals(PidResult.Value(100.0), ObdPidParser.parse(Sensor.SPEED, "41 0D 64\r>"))
        assertEquals(PidResult.Value(0.0), ObdPidParser.parse(Sensor.SPEED, "410D00>"))
        assertEquals(PidResult.Value(0.0), ObdPidParser.parse(Sensor.RPM, "410C0000>"))
    }
    @Test fun rejectsWrongPidTruncatedGarbageAndEcho() {
        for (raw in listOf("010C>", "410D32>", "410C1A>", "410CGGFF>", "X410C1AF8>", "410C1AF8F>", "SEARCHING...>"))
            assertIs<PidResult.Missing>(ObdPidParser.parse(Sensor.RPM, raw), raw)
    }
    @Test fun noDataAndElmErrorsAreMissing() {
        for (raw in listOf("NO DATA>", "STOPPED>", "?>", "CAN ERROR>", "UNABLE TO CONNECT>"))
            assertIs<PidResult.Missing>(ObdPidParser.parse(Sensor.SPEED, raw))
    }
    @Test fun supportMaskAndMultipleEcus() {
        assertEquals(setOf(Sensor.RPM, Sensor.SPEED), ObdPidParser.supported("0100\r41 00 00 18 00 00>"))
        assertEquals(setOf(Sensor.RPM), ObdPidParser.supported("410000100000>"))
        assertEquals(setOf(Sensor.RPM, Sensor.SPEED), ObdPidParser.supported("410000100000\r410000080000>"))
        assertEquals(emptySet(), ObdPidParser.supported("410000000000>"))
    }
    @Test fun missingSupportNeverAssumesSupport() {
        assertFailsWith<ElmProtocolException> { ObdPidParser.supported("NO DATA>") }
        assertFailsWith<ElmProtocolException> { ObdPidParser.supported("41000018>") }
    }
    @Test fun stableFirstEcuSelection() {
        assertEquals(PidResult.Value(50.0), ObdPidParser.parse(Sensor.SPEED, "410D32\r410D64>"))
    }
}
