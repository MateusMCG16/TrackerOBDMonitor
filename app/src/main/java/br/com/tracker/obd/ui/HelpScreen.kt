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
import br.com.tracker.obd.data.RedlineSetting
import br.com.tracker.obd.ui.components.label
import br.com.tracker.obd.ui.theme.*

/**
 * Four short topics behind a chip row instead of one long scroll. Only the
 * selected topic is composed, so the screen is always exactly one screen tall;
 * on short viewports the type scale tightens rather than the page growing.
 */
private enum class Topic(val chip: String, val title: String) {
    SESSION("Sessão", "ESTA SESSÃO"),
    VEHICLE("Veículo", "VEÍCULO E ADAPTADOR"),
    READING("Como ler", "COMO LER O CÓDIGO"),
    SCOPE("O que faz", "O QUE O DIAGNÓSTICO FAZ"),
    LIMITS("Limites", "LIMITES CONHECIDOS")
}

@Composable fun HelpScreen(state: MonitorState, redline: RedlineSetting?, onRedline: () -> Unit,
    modifier: Modifier = Modifier) {
    var topic by rememberSaveable { mutableStateOf(Topic.SESSION) }
    Column(modifier.fillMaxSize().background(Primer.BgDefault).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {

        ScreenHeader("Tracker / Ajuda", "Ajuda e sessão", "Somente leitura • veículo parado")

        ChipRow(Topic.entries.map { it.chip }, topic.ordinal) { topic = Topic.entries[it] }

        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val dense = maxHeight < 320.dp
            val body = if (dense) 11.sp else 12.5.sp
            val line = if (dense) 15.sp else 19.sp
            // Hugs its content: a panel stretched to the free height reads as empty.
            PrimerBox(Modifier.fillMaxWidth(), title = topic.title.lowercase().replaceFirstChar { it.uppercase() }) {
            Column(Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(if (dense) 5.dp else 9.dp)) {

                when (topic) {
                    Topic.SESSION -> {
                        Metric("Estado", state.phase.label())
                        Metric("Adaptador", state.deviceName ?: "nenhum dispositivo pareado selecionado")
                        Metric("Intervalo entre consultas", "${state.telemetry.slotMs} ms • meta de 5 Hz por sensor")
                        if (state.warnings.isEmpty()) Para("Nenhum aviso do adaptador nesta sessão.", body, line)
                        else state.warnings.forEach { Bullet(it, body, line, Primer.FgAttention) }
                        Para("Hz conta somente respostas válidas; a animação não cria medições.", body, line)
                    }
                    Topic.VEHICLE -> {
                        val v = state.vehicle
                        Metric("VIN", v.vin ?: if (state.live) "lendo…" else "abra com a sessão ativa")
                        Metric("Módulos", v.modules?.let { "$it responderam ao OBD" } ?: "--")
                        Metric("Protocolo", v.protocol ?: "--")
                        Metric("Adaptador", v.adapter ?: "--")
                        Metric("Linha vermelha", redline?.let { "${rpmText(it.rpm)} (${it.source.label})" }
                            ?: "não definida")
                        Para("O VIN é lido do carro e aparece só aqui; nada é enviado para fora do telefone.", body, line)
                        PrimerButton("Alterar linha vermelha", onRedline, Modifier.fillMaxWidth())
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
                        Bullet("Pede os modos 03, 07 e 0A para os códigos confirmados, pendentes e permanentes.", body, line)
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
}

@Composable private fun Para(text: String, size: TextUnit, line: TextUnit) =
    Text(text, color = Primer.FgDefault, fontSize = size, lineHeight = line)

@Composable private fun Bullet(text: String, size: TextUnit, line: TextUnit, accent: Color = Primer.FgMuted) {
    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        Text("•", color = accent, fontSize = size)
        Text(text, color = if (accent == Primer.FgMuted) Primer.FgDefault else accent, fontSize = size, lineHeight = line)
    }
}

@Composable private fun Legend(letter: String, meaning: String, size: TextUnit) {
    Row(horizontalArrangement = Arrangement.spacedBy(11.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(letter, color = Primer.FgDefault, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold,
            fontSize = size, modifier = Modifier.background(Primer.BgNeutralMuted, Primer.RadiusMedium)
                .padding(horizontal = 9.dp, vertical = 3.dp))
        Text(meaning, color = Primer.FgDefault, fontSize = size)
    }
}

@Composable private fun Metric(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, color = Primer.FgMuted, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Text(value, color = Primer.FgDefault, fontSize = 12.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1.3f))
    }
}
