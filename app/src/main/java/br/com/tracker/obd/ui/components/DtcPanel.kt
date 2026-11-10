package br.com.tracker.obd.ui.components

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
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.tracker.obd.data.*
import br.com.tracker.obd.ui.theme.*
import java.util.Locale

/**
 * The lamp and the one-line verdict. Fixed height, so the screen that holds it
 * can give every remaining pixel to the list of codes.
 */
@Composable internal fun DtcStatusRow(report: DtcReport, nowMs: Long, modifier: Modifier = Modifier) {
    val milOn = report.status?.milOn == true
    Row(modifier.background(if (milOn) Primer.BgDangerMuted else Primer.BgDefault, Primer.RadiusMedium)
        .border(1.dp, if (milOn) Primer.BorderDangerMuted else Primer.BorderDefault, Primer.RadiusMedium)
        .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MilLamp(milOn, 46.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(dtcHeadline(report), color = Primer.FgDefault, fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(dtcSubtitle(report, nowMs), color = Primer.FgMuted, fontSize = 12.sp, maxLines = 1)
        }
        report.status?.let { StateLabel(if (it.milOn) "Luz acesa" else "Luz apagada", if (it.milOn) Tone.DANGER else Tone.SUCCESS) }
    }
}

@Composable internal fun DtcCodeRow(code: DtcCode) {
    Row(Modifier.fillMaxWidth().background(Primer.BgDefault, Primer.RadiusMedium)
        .border(1.dp, Primer.BorderDefault, Primer.RadiusMedium).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(code.code, color = Primer.FgAttention, fontFamily = FontFamily.Monospace, fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold, modifier = Modifier.width(62.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(code.description ?: "Descrição não disponível neste aplicativo",
                color = if (code.description != null) Primer.FgDefault else Primer.FgMuted, fontSize = 13.sp,
                lineHeight = 18.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                PrimerLabel(code.kind.label, when (code.kind) {
                    DtcKind.STORED -> Tone.DANGER
                    DtcKind.PENDING -> Tone.ATTENTION
                    DtcKind.PERMANENT -> Tone.NEUTRAL
                })
                Text(code.origin, color = Primer.FgMuted, fontSize = 11.sp, maxLines = 1)
            }
        }
    }
}

/**
 * What the modules counted since the codes were last cleared. Three numbers in one
 * fixed-height row, so the list of codes below keeps every remaining pixel.
 */
@Composable internal fun DtcHistoryRow(extras: ExtraTelemetry, nowMs: Long, live: Boolean, modifier: Modifier = Modifier) {
    Row(modifier.background(Primer.BgMuted, Primer.RadiusMedium)
        .border(1.dp, Primer.BorderDefault, Primer.RadiusMedium)
        .padding(horizontal = 14.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        for ((signal, caption) in listOf(Signal.DISTANCE_SINCE_CLEAR to "km desde a limpeza",
            Signal.WARMUPS to "Aquecimentos", Signal.DISTANCE_WITH_MIL to "km com a luz acesa")) {
            val reading = extras.reading(signal)
            val fresh = live && reading.availability(nowMs) == Availability.VALID
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(caption, color = Primer.FgMuted, fontSize = 10.sp, maxLines = 1)
                Text(reading.value?.let { String.format(Locale.forLanguageTag("pt-BR"), "%,.0f", it) } ?: "--",
                    color = if (!fresh) Primer.FgMuted
                        else if (signal == Signal.DISTANCE_WITH_MIL && (reading.value ?: 0.0) > 0) Primer.FgAttention
                        else Primer.FgDefault, fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        }
    }
}

internal fun dtcHeadline(report: DtcReport) = when (report.phase) {
    DtcPhase.NEVER -> "Nunca lido"
    DtcPhase.READING -> "Consultando a ECU…"
    DtcPhase.UNSUPPORTED -> "Não atendido"
    DtcPhase.FAILED -> "Leitura falhou"
    DtcPhase.READY -> when (val n = report.codes.size) {
        0 -> "Sem falhas"
        1 -> "1 código"
        else -> "$n códigos"
    }
}

internal fun dtcSubtitle(report: DtcReport, nowMs: Long): String {
    val lamp = report.status?.let { if (it.milOn) "Luz de injeção acesa" else "Luz de injeção apagada" }
    val age = report.readAtMs?.let {
        String.format(Locale.US, "lido há %.0f s", (nowMs - it).coerceAtLeast(0) / 1000.0)
    }
    return listOfNotNull(lamp, age).joinToString(" • ").ifEmpty { "Toque em ler para consultar os módulos" }
}

/** The ISO malfunction indicator glyph, drawn so the panel needs no bitmap asset. */
@Composable internal fun MilLamp(on: Boolean, diameter: Dp = 58.dp) {
    val pulse = rememberInfiniteTransition(label = "mil")
    val glow by pulse.animateFloat(0.30f, 0.70f,
        infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "glow")
    val color = if (on) Primer.FgAttention else Primer.FgMuted.copy(alpha = 0.6f)
    Box(Modifier.size(diameter).clearAndSetSemantics {
        contentDescription = if (on) "Luz de injeção acesa" else "Luz de injeção apagada"
    }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            if (on) drawCircle(Brush.radialGradient(
                listOf(Primer.FgAttention.copy(alpha = glow * 0.5f), Color.Transparent), Offset(w / 2, h / 2), w / 2),
                w / 2, Offset(w / 2, h / 2))
            // The engine block in profile: a raised cam cover on top, cooling fins on
            // the left flank, the intake stub on the right. No dots inside — two of them
            // read as eyes at this size and turn the whole glyph into a face.
            val body = Path().apply {
                moveTo(w * 0.22f, h * 0.40f)
                lineTo(w * 0.38f, h * 0.40f)
                lineTo(w * 0.42f, h * 0.30f)
                lineTo(w * 0.58f, h * 0.30f)
                lineTo(w * 0.58f, h * 0.40f)
                lineTo(w * 0.72f, h * 0.40f)
                lineTo(w * 0.72f, h * 0.46f)
                lineTo(w * 0.84f, h * 0.46f)
                lineTo(w * 0.84f, h * 0.60f)
                lineTo(w * 0.72f, h * 0.60f)
                lineTo(w * 0.72f, h * 0.68f)
                lineTo(w * 0.30f, h * 0.68f)
                lineTo(w * 0.30f, h * 0.62f)
                lineTo(w * 0.16f, h * 0.62f)
                lineTo(w * 0.16f, h * 0.56f)
                lineTo(w * 0.22f, h * 0.56f)
                lineTo(w * 0.22f, h * 0.50f)
                lineTo(w * 0.16f, h * 0.50f)
                lineTo(w * 0.16f, h * 0.44f)
                lineTo(w * 0.22f, h * 0.44f)
                close()
            }
            drawPath(body, color.copy(alpha = if (on) 0.9f else 0.5f))
            drawPath(body, color, style = Stroke(1.6f))
        }
    }
}
