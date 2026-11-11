package br.com.tracker.obd

import br.com.tracker.obd.data.*
import kotlin.test.*

class RedlineTest {
    @Test fun scaleLeavesRoomAboveTheRedline() {
        assertEquals(8000, Redline.scaleFor(null))
        assertEquals(8000, Redline.scaleFor(6500))
        assertEquals(7000, Redline.scaleFor(6000))
        assertEquals(6000, Redline.scaleFor(4200))
        assertEquals(11000, Redline.scaleFor(10000))
    }

    @Test fun typedValuesOutsideAnEngineRangeAreRefused() {
        assertEquals(6500, Redline.parse("6500"))
        assertEquals(6500, Redline.parse("6.500"))
        assertEquals(6500, Redline.parse(" 6 500 rpm"))
        assertNull(Redline.parse(""))
        assertNull(Redline.parse("650"))
        assertNull(Redline.parse("2900"))
        assertNull(Redline.parse("12000"))
    }

    @Test fun suggestionRoundsTheMeasuredPeakDown() {
        assertEquals(6200, Redline.suggestion(6287.5))
        assertEquals(3000, Redline.suggestion(3000.0))
        assertNull(Redline.suggestion(2999.0))
    }

    @Test fun cutIsDetectedOnlyAfterARealRunUp() {
        val idle = listOf(800.0, 1500.0, 2900.0, 1000.0).fold(RedlinePeak()) { p, rpm -> p.add(rpm) }
        assertFalse(idle.cut, "a blip below 3 000 rpm is not a run to the cut")

        val run = listOf(800.0, 3000.0, 5200.0, 6250.0, 6300.0, 5600.0).fold(RedlinePeak()) { p, rpm -> p.add(rpm) }
        assertTrue(run.cut)
        assertEquals(6300.0, run.peak)
        assertEquals(6, run.samples)

        assertTrue(run.add(6000.0).cut, "once detected, the cut stays detected")
        assertEquals(run, run.add(Double.NaN))
    }
}
