package br.com.tracker.obd.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.tracker.obd.data.MonitorState
import br.com.tracker.obd.ui.theme.*

/**
 * Four short topics behind a chip row instead of one long scroll. Only the
 * selected topic is composed, so the screen is always exactly one screen tall;
 * on short viewports the type scale tightens rather than the page growing.
 */
private enum class Topic(val chip: String, val title: String) {
    SESSION("Sessão", "ESTA SESSÃO"),
    READING("Como ler", "COMO LER O CÓDIGO"),
    SCOPE("O que faz", "O QUE O DIAGNÓSTICO FAZ"),
    LIMITS("Limites", "LIMITES CONHECIDOS")
}

@Composable fun HelpScreen(state: MonitorState, modifier: Modifier = Modifier) {
    var topic by rememberSaveable { mutableStateOf(Topic.SESSION) }
    Column(modifier.fillMaxSize().background(Graphite).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {

        ScreenHeader("TRACKER / AJUDA", "Ajuda e sessão", "Somente leitura • veículo parado")

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Topic.entries.forEach { entry ->
                val selected = entry == topic
                Surface(onClick = { topic = entry }, modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    color = if (selected) Color(0xFF1B2A3E) else Panel,
                    border = androidx.compose.foundation.BorderStroke(1.dp,
                        if (selected) Cyan.copy(alpha = 0.5f) else Color(0xFF263449))) {
                    Text(entry.chip, color = if (selected) Cyan else Muted, fontSize = 11.sp, maxLines = 1,
                        modifier = Modifier.padding(vertical = 9.dp), textAlign =
                            androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
        }

        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val dense = maxHeight < 320.dp
            val body = if (dense) 11.sp else 12.5.sp
            val line = if (dense) 15.sp else 19.sp
            // Hugs its content: a panel stretched to the free height reads as empty.
            Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(20.dp))
                .border(1.dp, Color(0xFF263449), RoundedCornerShape(20.dp)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(if (dense) 5.dp else 9.dp)) {

                Text(topic.title, color = Cyan, fontSize = 10.sp, letterSpacing = 2.5.sp,
                    fontWeight = FontWeight.Bold)

                when (topic) {
                    Topic.SESSION -> {
                        Metric("Estado", state.phase.name.lowercase())
                        Metric("Adaptador", state.deviceName ?: "nenhum dispositivo pareado selecionado")
                        Metric("Intervalo entre consultas", "${state.telemetry.slotMs} ms • meta de 5 Hz por sensor")
                        if (state.warnings.isEmpty()) Para("Nenhum aviso do adaptador nesta sessão.", body, line)
                        else state.warnings.forEach { Bullet(it, body, line, Warning) }
                        Para("Hz conta somente respostas válidas; a animação não cria medições.", body, line)
                    }
                    Topic.READING -> {
                        Para("A primeira letra diz qual sistema reportou a falha, e o dígito seguinte diz " +
                            "quem definiu o código — 0 e 2 são da norma SAE, 1 é do fabricante.", body, line)
                        Legend("P", "Powertrain — motor, câmbio, emissões", body)
                        Legend("C", "Chassi — freios, tração, direção", body)
                        Legend("B", "Carroceria — conforto e conveniência", body)
                        Legend("U", "Rede — comunicação entre os módulos", body)
                    }
                    Topic.SCOPE -> {
                        Bullet("Pede o modo 01 PID 01 para o estado da luz de injeção e a contagem de códigos.", body, line)
                        Bullet("Pede o modo 03 para a lista de códigos armazenados.", body, line)
                        Bullet("As duas são requisições de leitura: nada é escrito, alterado ou apagado na ECU.", body, line)
                        Bullet("Não existe modo 04 aqui: o aplicativo não apaga códigos nem zera monitores.", body, line)
                    }
                    Topic.LIMITS -> {
                        Bullet("Cobre falhas de emissões — motor e câmbio. ABS, airbag e corpo usam " +
                            "serviços próprios da GM.", body, line)
                        Bullet("A lista é única: o aplicativo não diz qual módulo reportou cada código.", body, line)
                        Bullet("Códigos do fabricante aparecem sem descrição — não há tabela pública confiável.", body, line)
                        Bullet("5 Hz é meta, não garantia: clone, Bluetooth e ECU determinam a taxa real.", body, line)
                    }
                }
            }
        }
    }
}

@Composable private fun Para(text: String, size: TextUnit, line: TextUnit) =
    Text(text, color = Color(0xFFBFD6E8), fontSize = size, lineHeight = line)

@Composable private fun Bullet(text: String, size: TextUnit, line: TextUnit, accent: Color = Cyan) {
    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        Text("•", color = accent, fontSize = size)
        Text(text, color = if (accent == Cyan) Color(0xFFBFD6E8) else accent, fontSize = size, lineHeight = line)
    }
}

@Composable private fun Legend(letter: String, meaning: String, size: TextUnit) {
    Row(horizontalArrangement = Arrangement.spacedBy(11.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(letter, color = Cyan, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
            fontSize = size, modifier = Modifier.background(Color(0xFF16202F), RoundedCornerShape(7.dp))
                .padding(horizontal = 9.dp, vertical = 4.dp))
        Text(meaning, color = Color(0xFFD6E6FA), fontSize = size)
    }
}

@Composable private fun Metric(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, color = Muted, fontSize = 11.sp, modifier = Modifier.weight(1f))
        Text(value, color = Color(0xFFD6E6FA), fontSize = 11.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1.3f))
    }
}
