package br.com.tracker.obd.ui.components

import android.graphics.Paint
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.tracker.obd.data.*
import br.com.tracker.obd.ui.theme.*
import java.util.Locale
import kotlin.math.*

/**
 * The dial sizes itself to whatever box it is given: the caller hands it a
 * `weight(1f)` slot and the dial, its numerals and its tick labels all scale off
 * the measured space. Nothing here has a fixed aspect ratio, which is what lets
 * two gauges share one screen without that screen ever scrolling.
 */
@Composable internal fun InstrumentGauge(
    title: String, unit: String, maximum: Float, data: SensorTelemetry,
    nowMs: Long, live: Boolean, modifier: Modifier = Modifier, rpm: Boolean = false
) {
    val reading = data.reading
    val measuredValue = reading.value
    val quality = if (!live && reading.value != null) Availability.STALE else reading.availability(nowMs)
    val animated = remember { Animatable(0f) }
    LaunchedEffect(measuredValue, quality) {
        if (quality == Availability.VALID && measuredValue != null)
            animated.animateTo(measuredValue.toFloat(), tween(180, easing = LinearOutSlowInEasing))
        else animated.stop()
    }
    val value = if (quality == Availability.VALID) animated.value else reading.value?.toFloat()
    val live0 = quality == Availability.VALID
    // Velocidade em azul (accent) e rotação em verde (success), como no painel web.
    val color = if (!live0) Primer.FgMuted else if (rpm) Primer.FgSuccess else Primer.FgAccent
    val (status, tone) = when (quality) {
        Availability.VALID -> "Ao vivo" to Tone.SUCCESS
        Availability.STALE -> "Desatualizado" to Tone.ATTENTION
        Availability.UNAVAILABLE -> "Sem leitura" to Tone.NEUTRAL
    }
    PrimerBox(modifier, title = title, trailing = { StateLabel(status, tone) }) {
    Column(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {

        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f).clearAndSetSemantics {
            contentDescription = "$title: ${reading.value?.roundToInt() ?: "sem leitura"} $unit. $status"
        }, contentAlignment = Alignment.Center) {

            val side = min(maxWidth.value, maxHeight.value)
            Canvas(Modifier.fillMaxSize()) {
                val diameter = min(size.width, size.height) * 0.95f
                val radius = diameter / 2
                val center = Offset(size.width / 2, size.height / 2)
                val topLeft = center - Offset(radius, radius)
                val arcSize = Size(diameter, diameter)
                val band = diameter * 0.045f

                drawArc(Primer.BgNeutralMuted, 135f, 270f, false, topLeft, arcSize,
                    style = Stroke(band, cap = StrokeCap.Butt))
                if (rpm) drawArc(Primer.FgDanger.copy(alpha = 0.8f), 135f + 270 * 6500 / 8000, 270f * 1500 / 8000,
                    false, topLeft, arcSize, style = Stroke(band, cap = StrokeCap.Butt))
                if (value != null) {
                    val fraction = (value / maximum).coerceIn(0f, 1f)
                    val fill = if (rpm && live0 && value >= 6500f) Primer.FgDanger else color
                    if (fraction > 0.004f) drawArc(fill, 135f, 270f * fraction,
                        false, topLeft, arcSize, style = Stroke(band, cap = StrokeCap.Round))
                }

                val ticks = if (rpm) 40 else 44
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.color = Primer.FgMuted.toArgb()
                    textSize = (diameter * 0.055f).coerceAtLeast(7.sp.toPx())
                    textAlign = Paint.Align.CENTER
                    typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
                }
                for (i in 0..ticks) {
                    val angle = (135 + 270.0 * i / ticks) * PI / 180
                    val direction = Offset(cos(angle).toFloat(), sin(angle).toFloat())
                    val major = if (rpm) i % 5 == 0 else i % 4 == 0
                    val outer = radius - band * 1.8f
                    drawLine(if (rpm && i >= 33) Primer.FgDanger else Primer.FgMuted.copy(alpha = 0.7f), center + direction * outer,
                        center + direction * (outer - if (major) band else band * 0.45f),
                        if (major) 2f else 1f)
                    if (major) {
                        val labelRadius = radius - diameter * 0.155f
                        val label = if (rpm) (i / 5).toString() else (i * 5).toString()
                        drawContext.canvas.nativeCanvas.drawText(label, center.x + direction.x * labelRadius,
                            center.y + direction.y * labelRadius + paint.textSize / 3, paint)
                    }
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val text = value?.roundToInt()?.toString() ?: "--"
                // Width, not height, is what runs out first: "1071" needs a smaller
                // face than "1" to clear the dial, so the numeral scales per digit.
                val numeral = (side * 0.50f / max(2, text.length)).coerceIn(20f, 54f)
                Text(text, color = if (live0) Primer.FgDefault else Primer.FgMuted,
                    fontSize = numeral.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-1).sp, maxLines = 1)
                Text(unit, color = Primer.FgMuted, fontSize = (numeral * 0.24f).coerceIn(10f, 15f).sp,
                    letterSpacing = if (numeral < 40f) 1.sp else 2.sp, maxLines = 1)
            }
        }

        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Metric("Atualização", String.format(Locale.US, "%.1f Hz", if (live) data.hz else 0.0))
            Metric("Latência", data.latencyMs?.let { "$it ms" } ?: "--")
            Metric("Idade", reading.measuredAtMs?.let {
                String.format(Locale.US, "%.1f s", (nowMs - it).coerceAtLeast(0) / 1000.0)
            } ?: "--")
        }
        val footnote = reading.issue ?: if (rpm) "Faixa vermelha ilustrativa • escala × 1.000" else null
        if (footnote != null) Text(footnote, color = if (reading.issue != null) Primer.FgAttention else Primer.FgMuted,
            fontSize = 10.sp, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
    }
    }
}

@Composable private fun Metric(label: String, value: String) {
    Column {
        Text(label, color = Primer.FgMuted, fontSize = 10.sp, maxLines = 1)
        Text(value, color = Primer.FgDefault, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}
