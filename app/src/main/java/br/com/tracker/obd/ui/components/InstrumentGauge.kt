package br.com.tracker.obd.ui.components

import android.graphics.Paint
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
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
    val color = if (quality == Availability.VALID) Cyan else Muted
    val status = when (quality) { Availability.VALID -> "AO VIVO"; Availability.STALE -> "DESATUALIZADO"; Availability.UNAVAILABLE -> "SEM LEITURA" }
    Column(modifier.background(Brush.verticalGradient(listOf(Panel, Color(0xFF0F1724))), RoundedCornerShape(26.dp))
        .border(1.dp, Color(0xFF263449), RoundedCornerShape(26.dp)).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(title.uppercase(), color = Muted, fontSize = 12.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
            Text(status, color = if (quality == Availability.STALE) Warning else color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
        Box(Modifier.fillMaxWidth().widthIn(max = 400.dp).aspectRatio(1.19f)
            .clearAndSetSemantics { contentDescription = "$title: ${reading.value?.roundToInt() ?: "sem leitura"} $unit. $status" },
            contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val diameter = min(size.width, size.height * 1.08f) * 0.88f
                val radius = diameter / 2
                val center = Offset(size.width / 2, size.height * 0.55f)
                val topLeft = center - Offset(radius, radius)
                drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.07f), Color.Transparent), center, radius), radius, center)
                val arcSize = Size(diameter, diameter)
                drawArc(Color(0xFF263448), 135f, 270f, false, topLeft, arcSize, style = Stroke(9.dp.toPx(), cap = StrokeCap.Round))
                if (rpm) drawArc(Alert.copy(alpha = 0.75f), 135f + 270 * 6500 / 8000, 270f * 1500 / 8000,
                    false, topLeft, arcSize, style = Stroke(9.dp.toPx(), cap = StrokeCap.Butt))
                if (value != null) {
                    val fraction = (value / maximum).coerceIn(0f, 1f)
                    drawArc(Brush.sweepGradient(listOf(Electric, Cyan, Electric), center), 135f, 270f * fraction,
                        false, topLeft, arcSize, style = Stroke(9.dp.toPx(), cap = StrokeCap.Round))
                    val angle = (135 + 270 * fraction) * PI / 180
                    val end = center + Offset(cos(angle).toFloat(), sin(angle).toFloat()) * radius
                    drawCircle(color.copy(alpha = 0.2f), 12.dp.toPx(), end)
                    drawCircle(color, 4.dp.toPx(), end)
                }
                val ticks = if (rpm) 40 else 44
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.color = Muted.toArgb(); textSize = 11.sp.toPx(); textAlign = Paint.Align.CENTER
                    typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
                }
                for (i in 0..ticks) {
                    val angle = (135 + 270.0 * i / ticks) * PI / 180
                    val direction = Offset(cos(angle).toFloat(), sin(angle).toFloat())
                    val major = if (rpm) i % 5 == 0 else i % 4 == 0
                    val outer = radius - 16.dp.toPx()
                    drawLine(if (rpm && i >= 33) Alert else Color(0xFF5D708A), center + direction * outer,
                        center + direction * (outer - if (major) 9.dp.toPx() else 4.dp.toPx()), if (major) 2f else 1f)
                    if (major) {
                        val labelRadius = radius - 40.dp.toPx()
                        val label = if (rpm) (i / 5).toString() else (i * 5).toString()
                        drawContext.canvas.nativeCanvas.drawText(label, center.x + direction.x * labelRadius,
                            center.y + direction.y * labelRadius + paint.textSize / 3, paint)
                    }
                }
            }
            Column(Modifier.offset(y = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(value?.roundToInt()?.toString() ?: "--", color = if (quality == Availability.VALID) Color(0xFFF3FAFF) else Muted,
                    fontSize = if (rpm) 51.sp else 70.sp, fontWeight = FontWeight.Light, letterSpacing = (-2).sp)
                Text(unit, color = color, fontSize = 15.sp, letterSpacing = 2.sp)
            }
        }
        if (rpm) {
            LinearProgressIndicator(progress = { ((value ?: 0f) / maximum).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(4.dp), color = color, trackColor = Color(0xFF29364A))
            Spacer(Modifier.height(8.dp))
            Text("Faixa vermelha ilustrativa • escala × 1.000", color = Muted, fontSize = 10.sp)
        } else Text("TELEMETRIA DO VEÍCULO", color = Muted, fontSize = 10.sp, letterSpacing = 2.sp)
        HorizontalDivider(Modifier.padding(vertical = 14.dp), color = Color(0xFF28354A))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Metric("ATUALIZAÇÃO", String.format(Locale.US, "%.1f Hz", if (live) data.hz else 0.0))
            Metric("LATÊNCIA", data.latencyMs?.let { "$it ms" } ?: "--")
            Metric("IDADE", reading.measuredAtMs?.let { String.format(Locale.US, "%.1f s", (nowMs - it).coerceAtLeast(0) / 1000.0) } ?: "--")
        }
        reading.issue?.let { Text(it, color = Warning, fontSize = 11.sp, modifier = Modifier.padding(top = 10.dp)) }
    }
}
@Composable private fun Metric(label: String, value: String) {
    Column { Text(label, color = Muted, fontSize = 9.sp, letterSpacing = 0.7.sp)
        Text(value, color = Color(0xFFD6E6FA), fontSize = 13.sp, fontWeight = FontWeight.Medium) }
}
