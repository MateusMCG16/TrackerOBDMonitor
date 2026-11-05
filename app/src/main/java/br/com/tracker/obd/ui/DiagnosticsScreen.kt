package br.com.tracker.obd.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.tracker.obd.data.*
import br.com.tracker.obd.ui.components.*
import br.com.tracker.obd.ui.theme.*

/**
 * Fixed header, fixed status row, fixed button; the list of codes takes the rest.
 * The screen itself never scrolls — the code list is the only scrollable region,
 * because the number of stored codes is the one thing this screen cannot bound.
 */
@Composable fun DiagnosticsScreen(state: MonitorState, onRead: () -> Unit, modifier: Modifier = Modifier) {
    val report = state.dtc
    val reading = report.phase == DtcPhase.READING
    Column(modifier.fillMaxSize().background(Graphite).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {

        ScreenHeader("TRACKER / DIAGNÓSTICO", "Códigos de falha",
            "${state.phase.label()} • ${state.deviceName ?: "nenhum dispositivo"}")

        DtcStatusRow(report, state.telemetry.nowMs, Modifier.fillMaxWidth())

        // Top-aligned: a hint centred in the free space reads as a loading state.
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.TopCenter) {
            when {
                report.codes.isNotEmpty() -> LazyColumn(Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(report.codes, key = { it.code + it.origin }) { DtcCodeRow(it) }
                }
                !state.live -> Hint(Warning, "Sem sessão ativa. Vá para Instrumentos, conecte o ELM327 " +
                    "ou abra o modo demo, e volte aqui para consultar.")
                report.phase == DtcPhase.READY -> Hint(Cyan,
                    "Nenhum código armazenado nos módulos que responderam.")
                else -> Hint(Muted, "Toque em ler para pedir o estado da luz de injeção (modo 01 PID 01) " +
                    "e a lista de códigos (modo 03). Nada é escrito na ECU.")
            }
        }

        report.message?.let {
            Text(it, color = if (report.phase == DtcPhase.READY) Muted else Warning,
                fontSize = 11.sp, maxLines = 2)
        }

        Button(onClick = onRead, enabled = state.live && !reading, modifier = Modifier.fillMaxWidth()) {
            if (reading) {
                CircularProgressIndicator(Modifier.size(15.dp), strokeWidth = 2.dp, color = Graphite)
                Spacer(Modifier.width(10.dp))
                Text("Lendo…")
            } else Text(if (report.phase == DtcPhase.NEVER) "Ler códigos de falha" else "Ler novamente")
        }
    }
}

@Composable private fun Hint(accent: Color, text: String) {
    Row(Modifier.fillMaxWidth().background(accent.copy(alpha = 0.10f), RoundedCornerShape(16.dp))
        .padding(15.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(8.dp).background(accent, RoundedCornerShape(4.dp)))
        Text(text, color = accent, fontSize = 12.sp, lineHeight = 18.sp)
    }
}
