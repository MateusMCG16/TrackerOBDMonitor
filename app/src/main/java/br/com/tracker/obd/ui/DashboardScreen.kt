package br.com.tracker.obd.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import br.com.tracker.obd.data.*
import br.com.tracker.obd.bluetooth.BluetoothLink
import br.com.tracker.obd.ui.components.*
import br.com.tracker.obd.ui.theme.*

@Composable fun DashboardScreen(state: MonitorState, link: BluetoothLink, onConnect: () -> Unit, onDemo: () -> Unit, onDisconnect: () -> Unit) {
    val active = state.phase !in setOf(ConnectionPhase.DISCONNECTED, ConnectionPhase.ERROR)
    val live = state.phase in setOf(ConnectionPhase.RECEIVING, ConnectionPhase.WAITING, ConnectionPhase.DEMO)
    val t = state.telemetry
    BoxWithConstraints(Modifier.fillMaxSize().background(Graphite).safeDrawingPadding()) {
        val wide = maxWidth >= 680.dp
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(if (wide) 28.dp else 18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("TRACKER / LIVE INSTRUMENTS", color = Cyan, fontSize = 10.sp, letterSpacing = 3.sp)
                Text("Tracker OBD Monitor", style = MaterialTheme.typography.titleLarge)
                ConnectionStatus(state.phase)
                Text(if (state.phase == ConnectionPhase.DEMO) "Simulador local • sem conexão com o veículo"
                    else "Bluetooth ${when(link) { BluetoothLink.CONNECTED -> "conectado"; BluetoothLink.CONNECTING -> "conectando"; else -> "desconectado" }} • ${state.deviceName ?: "nenhum dispositivo"}",
                    color = Muted, fontSize = 12.sp)
                Text(String.format(Locale.US, "Velocidade %.1f Hz  •  RPM %.1f Hz",
                    if (live) t.speed.hz else 0.0, if (live) t.rpm.hz else 0.0), color = Cyan, fontSize = 11.sp)
            }
            if (state.phase == ConnectionPhase.DEMO) {
                Surface(color = Warning.copy(alpha = 0.12f), shape = RoundedCornerShape(12.dp)) {
                    Text("MODO DEMONSTRAÇÃO • DADOS SIMULADOS", Modifier.padding(14.dp), color = Warning, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            state.message?.let { Text(it, color = if (state.phase == ConnectionPhase.ERROR) Alert else Muted, fontSize = 13.sp) }
            if (wide) Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                SpeedGauge(t.speed, t.nowMs, live, Modifier.weight(1f))
                RpmGauge(t.rpm, t.nowMs, live, Modifier.weight(1f))
            } else {
                SpeedGauge(t.speed, t.nowMs, live, Modifier.fillMaxWidth())
                RpmGauge(t.rpm, t.nowMs, live, Modifier.fillMaxWidth())
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (active) {
                    Button(onClick = onDisconnect, modifier = Modifier.weight(1f)) { Text(if (state.phase == ConnectionPhase.CONNECTING) "Cancelar conexão" else "Desconectar") }
                } else {
                    Button(onClick = onConnect, modifier = Modifier.weight(1f)) { Text("Conectar ELM327") }
                    OutlinedButton(onClick = onDemo, modifier = Modifier.weight(1f)) { Text("Modo demo") }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("META 5 Hz / SENSOR", color = Cyan, fontSize = 11.sp, letterSpacing = 1.sp)
                Text("${t.slotMs} ms entre consultas • sem requisições simultâneas", color = Muted, fontSize = 11.sp)
                Text("Hz conta respostas válidas. A animação não cria medições.", color = Muted, fontSize = 11.sp)
                state.warnings.forEach { Text(it, color = Warning, fontSize = 11.sp) }
                HorizontalDivider(Modifier.padding(vertical = 6.dp), color = Color(0xFF253147))
                Text("SOMENTE LEITURA • Faça os primeiros testes com o veículo estacionado. Não interaja durante a condução.", color = Muted, fontSize = 11.sp)
            }
        }
    }
}
