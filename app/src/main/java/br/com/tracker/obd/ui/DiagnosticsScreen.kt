package br.com.tracker.obd.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.tracker.obd.data.*
import br.com.tracker.obd.ui.components.DtcPanel
import br.com.tracker.obd.ui.components.label
import br.com.tracker.obd.ui.theme.*

@Composable fun DiagnosticsScreen(state: MonitorState, onRead: () -> Unit, onBack: () -> Unit) {
    val live = state.phase in setOf(ConnectionPhase.RECEIVING, ConnectionPhase.WAITING, ConnectionPhase.DEMO)
    Column(Modifier.fillMaxSize().background(Graphite).safeDrawingPadding()
        .verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)) {

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TextButton(onClick = onBack, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)) {
                BackArrow()
                Spacer(Modifier.width(8.dp))
                Text("Instrumentos", fontSize = 13.sp)
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("TRACKER / DIAGNÓSTICO", color = Cyan, fontSize = 10.sp, letterSpacing = 3.sp)
            Text("Códigos de falha da ECU", style = MaterialTheme.typography.titleLarge)
            Text("${state.phase.label()} • ${state.deviceName ?: "nenhum dispositivo"}",
                color = Muted, fontSize = 12.sp)
        }

        if (!live) InfoCard(Warning,
            "Sem sessão ativa. Volte aos instrumentos, conecte o ELM327 (ou abra o modo demo) e retorne aqui.")

        DtcPanel(state.dtc, state.telemetry.nowMs, live, onRead, Modifier.fillMaxWidth())

        Section("COMO LER O CÓDIGO") {
            Text("A primeira letra diz qual sistema reportou a falha, e o dígito seguinte diz quem definiu " +
                "o código — 0 e 2 são da norma SAE, 1 é do fabricante.", color = Color(0xFFBFD6E8),
                fontSize = 12.sp, lineHeight = 18.sp)
            Spacer(Modifier.height(4.dp))
            Legend("P", "Powertrain — motor, câmbio, emissões")
            Legend("C", "Chassi — freios, tração, direção")
            Legend("B", "Carroceria — conforto e conveniência")
            Legend("U", "Rede — comunicação entre os módulos")
        }

        Section("O QUE ESTA TELA FAZ") {
            Bullet("Pede o modo 01 PID 01 para saber o estado da luz de injeção e quantos códigos existem.")
            Bullet("Pede o modo 03 para a lista de códigos armazenados.")
            Bullet("As duas são requisições de leitura: nada é escrito, alterado ou apagado na ECU.")
        }

        Section("LIMITES CONHECIDOS") {
            Bullet("Cobre falhas relacionadas a emissões — essencialmente motor e câmbio. ABS, airbag e " +
                "corpo usam serviços próprios da GM e não aparecem aqui.")
            Bullet("Códigos de módulos diferentes vêm numa lista única: o aplicativo não identifica qual " +
                "módulo reportou cada um.")
            Bullet("Códigos específicos do fabricante aparecem sem descrição, porque não existe tabela " +
                "pública confiável para embutir.")
            Bullet("O aplicativo não apaga códigos. Apagar exige o modo 04, que escreve na ECU e também " +
                "zera os monitores de prontidão.")
        }

        Text("SOMENTE LEITURA • Faça a consulta com o veículo parado.", color = Muted, fontSize = 11.sp,
            modifier = Modifier.padding(bottom = 8.dp))
    }
}

@Composable private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(20.dp))
        .border(1.dp, Color(0xFF263449), RoundedCornerShape(20.dp)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = Cyan, fontSize = 10.sp, letterSpacing = 2.5.sp, fontWeight = FontWeight.Bold)
        content()
    }
}

@Composable private fun Legend(letter: String, meaning: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(letter, color = Cyan, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
            fontSize = 14.sp, modifier = Modifier.background(Color(0xFF16202F), RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 5.dp))
        Text(meaning, color = Color(0xFFD6E6FA), fontSize = 12.sp)
    }
}

@Composable private fun Bullet(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("•", color = Cyan, fontSize = 12.sp)
        Text(text, color = Color(0xFFBFD6E8), fontSize = 12.sp, lineHeight = 18.sp)
    }
}

@Composable private fun InfoCard(accent: Color, text: String) {
    Row(Modifier.fillMaxWidth().background(accent.copy(alpha = 0.10f), RoundedCornerShape(16.dp))
        .padding(15.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(8.dp).background(accent, RoundedCornerShape(4.dp)))
        Text(text, color = accent, fontSize = 12.sp, lineHeight = 18.sp)
    }
}

@Composable private fun BackArrow() {
    Canvas(Modifier.size(14.dp)) {
        val path = Path().apply {
            moveTo(size.width * 0.62f, size.height * 0.18f)
            lineTo(size.width * 0.26f, size.height * 0.5f)
            lineTo(size.width * 0.62f, size.height * 0.82f)
        }
        drawPath(path, Cyan, style = Stroke(2.2f, cap = StrokeCap.Round))
    }
}
