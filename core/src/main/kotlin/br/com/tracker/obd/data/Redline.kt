package br.com.tracker.obd.data

import kotlin.math.ceil

/** How the user arrived at the redline: OBD-II has no PID for it, so it is never read from the car. */
enum class RedlineSource(val label: String) { MEASURED("medida no corte"), TYPED("digitada") }

data class RedlineSetting(val rpm: Int, val source: RedlineSource)

object Redline {
    /** A typed or measured value outside this range is a typo or a misread, not an engine. */
    val VALID_RANGE = 3000..10000

    /** Peaks below this are a blip of the throttle, not a run up to the cut. */
    const val MIN_PEAK = 3000.0

    /** A drop this far below the peak after it means the engine was cut or the throttle released. */
    const val CUT_DROP = 600.0

    /** The tachometer's full scale: the redline plus at least 1 000 rpm, in whole thousands. */
    fun scaleFor(redline: Int?): Int {
        if (redline == null) return 8000
        return (ceil((redline + 1000) / 1000.0).toInt() * 1000).coerceIn(6000, 11000)
    }

    /** Parses what the user typed; null when it is not a plausible redline. */
    fun parse(text: String): Int? = text.filter { it.isDigit() }.takeIf { it.length in 4..5 }
        ?.toIntOrNull()?.takeIf { it in VALID_RANGE }

    /** The measured peak rounded down to the hundred, so a sample on a bounce is not overstated. */
    fun suggestion(peak: Double): Int? = ((peak / 100).toInt() * 100).takeIf { it in VALID_RANGE }
}

/**
 * Follows the RPM samples while the user revs to the cut. At 5 Hz the very top
 * of the cut can fall between two samples, so the peak is a floor, not the exact
 * limiter value.
 */
data class RedlinePeak(val peak: Double = 0.0, val cut: Boolean = false, val samples: Int = 0) {
    fun add(rpm: Double): RedlinePeak {
        if (rpm.isNaN() || rpm < 0) return this
        val top = maxOf(peak, rpm)
        val dropped = top >= Redline.MIN_PEAK && top - rpm >= Redline.CUT_DROP
        return RedlinePeak(top, cut || dropped, samples + 1)
    }
}
