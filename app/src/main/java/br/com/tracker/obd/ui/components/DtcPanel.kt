package br.com.tracker.obd.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.tracker.obd.data.*
import br.com.tracker.obd.ui.theme.*
import java.util.Locale

@Composable fun DtcPanel(report: DtcReport, nowMs: Long, enabled: Boolean, onRead: () -> Unit,
                         modifier: Modifier = Modifier) {
    val reading = report.phase == DtcPhase.READING
    val milOn = report.status?.milOn == true
    Column(modifier.background(Brush.verticalGradient(listOf(Panel, Color(0xFF0F1724))), RoundedCornerShape(26.dp))
        .border(1.dp, if (milOn) Warning.copy(alpha = 0.45f) else Color(0xFF263449), RoundedCornerShape(26.dp))
        .padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("DIAGNÓSTICO DA ECU", color = Cyan, fontSize = 10.sp, letterSpacing = 2.5.sp,
                    fontWeight = FontWeight.Bold)
                Text(headline(report), color = Color(0xFFEAF4FF), fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                Text(subtitle(report, nowMs), color = Muted, fontSize = 11.sp)
            }
            MilLamp(milOn)
        }

        if (report.codes.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            report.codes.forEach { CodeRow(it) }
        }

        AnimatedVisibility(report.phase == DtcPhase.READY && report.codes.isEmpty(),
            enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Row(Modifier.fillMaxWidth().background(Color(0xFF12222B), RoundedCornerShape(14.dp)).padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(Cyan, RoundedCornerShape(4.dp)))
                Text("Nenhum código armazenado nos módulos que responderam.", color = Color(0xFFBFD6E8), fontSize = 12.sp)
            }
        }

        report.message?.let {
            Text(it, color = if (report.phase == DtcPhase.READY) Muted else Warning, fontSize = 11.sp)
        }

        Button(onClick = onRead, enabled = enabled && !reading, modifier = Modifier.fillMaxWidth()) {
            if (reading) {
                CircularProgressIndicator(Modifier.size(15.dp), strokeWidth = 2.dp, color = Graphite)
                Spacer(Modifier.width(10.dp))
                Text("Lendo…")
            } else Text(if (report.phase == DtcPhase.NEVER) "Ler códigos de falha" else "Ler novamente")
        }

        Text("Somente leitura • modo 01 PID 01 e modo 03. O aplicativo não apaga códigos.\n" +
            "Cobre falhas de emissões (motor e câmbio); ABS e airbag não aparecem aqui.",
            color = Muted, fontSize = 10.sp, lineHeight = 15.sp)
    }
}

private fun headline(report: DtcReport) = when (report.phase) {
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

private fun subtitle(report: DtcReport, nowMs: Long): String {
    val lamp = report.status?.let { if (it.milOn) "Luz de injeção acesa" else "Luz de injeção apagada" }
    val age = report.readAtMs?.let {
        String.format(Locale.US, "lido há %.0f s", (nowMs - it).coerceAtLeast(0) / 1000.0)
    }
    return listOfNotNull(lamp, age).joinToString(" • ").ifEmpty { "Toque para consultar os módulos" }
}

@Composable private fun CodeRow(code: DtcCode) {
    Row(Modifier.fillMaxWidth().background(Color(0xFF16202F), RoundedCornerShape(14.dp))
        .border(1.dp, Color(0xFF26354B), RoundedCornerShape(14.dp)).padding(13.dp),
        horizontalArrangement = Arrangement.spacedBy(13.dp)) {
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

/** The ISO malfunction indicator glyph, drawn so the panel needs no bitmap asset. */
@Composable private fun MilLamp(on: Boolean) {
    val pulse = rememberInfiniteTransition(label = "mil")
    val glow by pulse.animateFloat(0.30f, 0.70f,
        infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "glow")
    val color = if (on) Warning else Color(0xFF3A4860)
    Box(Modifier.size(58.dp).clearAndSetSemantics {
        contentDescription = if (on) "Luz de injeção acesa" else "Luz de injeção apagada"
    }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            if (on) drawCircle(Brush.radialGradient(
                listOf(Warning.copy(alpha = glow * 0.5f), Color.Transparent), Offset(w / 2, h / 2), w / 2),
                w / 2, Offset(w / 2, h / 2))
            val body = Path().apply {
                moveTo(w * 0.26f, h * 0.42f)
                lineTo(w * 0.34f, h * 0.42f)
                lineTo(w * 0.38f, h * 0.32f)
                lineTo(w * 0.52f, h * 0.32f)
                lineTo(w * 0.56f, h * 0.42f)
                lineTo(w * 0.70f, h * 0.42f)
                lineTo(w * 0.70f, h * 0.50f)
                lineTo(w * 0.76f, h * 0.50f)
                lineTo(w * 0.76f, h * 0.62f)
                lineTo(w * 0.70f, h * 0.62f)
                lineTo(w * 0.70f, h * 0.70f)
                lineTo(w * 0.30f, h * 0.70f)
                lineTo(w * 0.30f, h * 0.62f)
                lineTo(w * 0.22f, h * 0.62f)
                lineTo(w * 0.22f, h * 0.50f)
                lineTo(w * 0.26f, h * 0.50f)
                close()
            }
            drawPath(body, color.copy(alpha = if (on) 0.9f else 0.5f))
            drawPath(body, color, style = Stroke(1.6f))
            // The two bolts the symbol carries on its left flank.
            drawCircle(Graphite, w * 0.025f, Offset(w * 0.37f, h * 0.55f))
            drawCircle(Graphite, w * 0.025f, Offset(w * 0.47f, h * 0.55f))
        }
    }
}
