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
    Row(modifier.background(Brush.verticalGradient(listOf(Panel, Color(0xFF0F1724))), RoundedCornerShape(22.dp))
        .border(1.dp, if (milOn) Warning.copy(alpha = 0.45f) else Color(0xFF263449), RoundedCornerShape(22.dp))
        .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MilLamp(milOn, 46.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("ESTADO ATUAL", color = Cyan, fontSize = 9.sp, letterSpacing = 2.5.sp, fontWeight = FontWeight.Bold)
            Text(dtcHeadline(report), color = Color(0xFFEAF4FF), fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(dtcSubtitle(report, nowMs), color = Muted, fontSize = 10.sp, maxLines = 1)
        }
    }
}

@Composable internal fun DtcCodeRow(code: DtcCode) {
    Row(Modifier.fillMaxWidth().background(Color(0xFF16202F), RoundedCornerShape(14.dp))
        .border(1.dp, Color(0xFF26354B), RoundedCornerShape(14.dp)).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(code.code, color = Cyan, fontFamily = FontFamily.Monospace, fontSize = 15.sp,
            fontWeight = FontWeight.Bold, modifier = Modifier.width(62.dp))
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(code.description ?: "Descrição não disponível neste aplicativo",
                color = if (code.description != null) Color(0xFFE2EEF9) else Muted, fontSize = 13.sp,
                lineHeight = 18.sp)
            Text(code.origin, color = Muted, fontSize = 10.sp, letterSpacing = 0.4.sp)
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
    val color = if (on) Warning else Color(0xFF3A4860)
    Box(Modifier.size(diameter).clearAndSetSemantics {
        contentDescription = if (on) "Luz de injeção acesa" else "Luz de injeção apagada"
    }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            if (on) drawCircle(Brush.radialGradient(
                listOf(Warning.copy(alpha = glow * 0.5f), Color.Transparent), Offset(w / 2, h / 2), w / 2),
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
