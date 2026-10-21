package br.com.tracker.obd

import br.com.tracker.obd.data.*
import br.com.tracker.obd.obd.*
import kotlin.test.*

/** The 010C0D request and the stream quirks the ELM327 datasheet warns about. */
class ObdMultiPidTest {
    private val both = listOf(Sensor.RPM, Sensor.SPEED)

    @Test fun oneCanFrameCarriesRpmAndSpeed() {
        val parsed = ObdPidParser.parseAll("410C1AF80D64>", both)
        assertEquals(PidResult.Value(1726.0), parsed.getValue(Sensor.RPM))
        assertEquals(PidResult.Value(100.0), parsed.getValue(Sensor.SPEED))
    }
    @Test fun orderOfPidsInTheReplyDoesNotMatter() {
        val parsed = ObdPidParser.parseAll("41 0D 00 0C 0A F8\r>", both)
        assertEquals(PidResult.Value(0.0), parsed.getValue(Sensor.SPEED))
        assertEquals(PidResult.Value(702.0), parsed.getValue(Sensor.RPM))
    }
    @Test fun multilineReplyIsReassembled() {
        val parsed = ObdPidParser.parseAll("006\r0:410C1AF80D\r1:64\r>", both)
        assertEquals(PidResult.Value(1726.0), parsed.getValue(Sensor.RPM))
        assertEquals(PidResult.Value(100.0), parsed.getValue(Sensor.SPEED))
    }
    @Test fun trailingPaddingIsIgnored() {
        val parsed = ObdPidParser.parseAll("410C1AF80D6400000000>", both)
        assertEquals(PidResult.Value(1726.0), parsed.getValue(Sensor.RPM))
        assertEquals(PidResult.Value(100.0), parsed.getValue(Sensor.SPEED))
    }
    @Test fun aPartialReplyLeavesTheOtherSensorMissing() {
        val parsed = ObdPidParser.parseAll("410C1AF8>", both)
        assertEquals(PidResult.Value(1726.0), parsed.getValue(Sensor.RPM))
        assertIs<PidResult.Missing>(parsed.getValue(Sensor.SPEED))
    }
    @Test fun elmErrorsMarkEverySensorRequested() {
        for (raw in listOf("NO DATA>", "?>", "CAN ERROR>"))
            assertTrue(ObdPidParser.parseAll(raw, both).values.all { it is PidResult.Missing }, raw)
    }
    @Test fun strayNullBytesNeverDiscardAValidReading() {
        // The datasheet (p. 9) warns that the ELM327 may insert NULs into the stream.
        assertEquals(PidResult.Value(1726.0), ObdPidParser.parse(Sensor.RPM, "\u0000410C1A\u0000F8\u0000>"))
        assertEquals(PidResult.Value(100.0), ObdPidParser.parse(Sensor.SPEED, "41\u00000D64\r\u0000>"))
        assertEquals(setOf(Sensor.RPM, Sensor.SPEED), ObdPidParser.supported("\u0000410000180000>"))
    }
    @Test fun canProtocolIsRecognisedIncludingTheAutomaticPrefix() {
        for (raw in listOf("6>", "A6>", "8>", "AC>"))
            assertTrue(ObdPidParser.isCanProtocol(raw), raw)
        for (raw in listOf("1>", "A3>", "5>", "?>", "ATDPN>"))
            assertFalse(ObdPidParser.isCanProtocol(raw), raw)
    }
}
