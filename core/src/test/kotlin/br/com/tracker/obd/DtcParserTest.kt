package br.com.tracker.obd

import br.com.tracker.obd.obd.*
import kotlin.test.*

class DtcParserTest {
    @Test fun statusSplitsMilBitFromCount() {
        // 81 = lamp on plus one stored code (datasheet, p. 35).
        val status = DtcParser.status("41 01 81 07 65 04\r>")
        assertTrue(status.milOn)
        assertEquals(1, status.count)
    }
    @Test fun statusWithoutLampAndWithNoCodes() {
        assertEquals(false, DtcParser.status("410100076504>").milOn)
        assertEquals(0, DtcParser.status("410100076504>").count)
        assertEquals(5, DtcParser.status("410105076504>").count)
    }
    @Test fun statusAddsUpModulesAndKeepsAnyLitLamp() {
        val status = DtcParser.status("410102076504\r410182000000\r>")
        assertTrue(status.milOn)
        assertEquals(4, status.count)
    }
    @Test fun statusRefusesErrorsAndShortFrames() {
        for (raw in listOf("NO DATA>", "?>", "4101>", "41 01>", "SEARCHING...>"))
            assertFailsWith<ElmProtocolException>(raw) { DtcParser.status(raw) }
    }

    @Test fun legacyReplyHasNoCountByteAndIsPadded() {
        // The datasheet's own example: 43 01 33 00 00 00 00 is P0133 plus padding.
        assertEquals(listOf("P0133"), DtcParser.codes("43 01 33 00 00 00 00\r>", can = false))
    }
    @Test fun canReplyStartsWithTheNumberOfCodes() {
        assertEquals(listOf("P0133", "P0196"), DtcParser.codes("43 02 01 33 01 96\r>", can = true))
        assertEquals(emptyList(), DtcParser.codes("4300>", can = true))
    }
    @Test fun canCountNeverReadsPastTheFrame() {
        // A clone claiming six codes in a frame that holds one must not invent five.
        assertEquals(listOf("P0133"), DtcParser.codes("43060133>", can = true))
    }
    @Test fun canCountShorterThanTheFrameWins() {
        // Padding beyond the declared count is not data.
        assertEquals(listOf("P0133"), DtcParser.codes("4301013301960000>", can = true))
    }
    @Test fun everyLetterAndManufacturerRange() {
        assertEquals("P0133", DtcParser.decode("0133"))
        assertEquals("P1131", DtcParser.decode("1131"))
        assertEquals("P2096", DtcParser.decode("2096"))
        assertEquals("C0035", DtcParser.decode("4035"))
        assertEquals("B1000", DtcParser.decode("9000"))
        assertEquals("U0100", DtcParser.decode("C100"))
        assertEquals("U1016", DtcParser.decode("D016"))
        assertEquals("U3003", DtcParser.decode("F003"))
    }
    @Test fun paddingAndGarbageAreNotCodes() {
        assertNull(DtcParser.decode("0000"))
        assertNull(DtcParser.decode("01"))
        assertNull(DtcParser.decode("01GG"))
        assertNull(DtcParser.decode("013333"))
    }
    @Test fun noDataMeansNoStoredCodes() {
        for (raw in listOf("NO DATA>", "43>", "SEARCHING...\rNO DATA\r>"))
            assertEquals(emptyList(), DtcParser.codes(raw, can = true), raw)
    }
    @Test fun severalModulesEachAnswerOnItsOwnLine() {
        assertEquals(listOf("P0133", "U0100"), DtcParser.codes("4301 0133\r4301 C100\r>", can = true))
    }
    @Test fun theSameCodeFromTwoModulesIsListedOnce() {
        assertEquals(listOf("P0133"), DtcParser.codes("43010133\r43010133\r>", can = true))
    }
    @Test fun multiFrameReplyIsReassembled() {
        // Five codes do not fit one CAN frame: a length line, then 0: / 1: … frames.
        val raw = "00C\r0: 43 05 01 33 01 96 02\r1: 34 03 00 04 20 05 05\r2: 00 00 00 00 00 00 00\r>"
        assertEquals(listOf("P0133", "P0196", "P0234", "P0300", "P0420"), DtcParser.codes(raw, can = true))
    }

    @Test fun descriptionsCoverGenericCodesAndOwnUpToGaps() {
        assertEquals("Sonda lambda — resposta lenta (banco 1, sensor 1)", DtcDescriptions.of("P0133"))
        assertNull(DtcDescriptions.of("P1631"))
        assertEquals("Específico do fabricante (GM)", DtcDescriptions.origin("P1631"))
        assertEquals("Powertrain definido pela SAE", DtcDescriptions.origin("P0133"))
        assertEquals("Rede de comunicação entre módulos", DtcDescriptions.origin("U0100"))
    }
}
