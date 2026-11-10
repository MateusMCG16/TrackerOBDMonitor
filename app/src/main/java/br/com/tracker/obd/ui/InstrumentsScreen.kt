package br.com.tracker.obd.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.tracker.obd.bluetooth.BluetoothLink
import br.com.tracker.obd.data.*
import br.com.tracker.obd.ui.components.*
import br.com.tracker.obd.ui.theme.*

/**
 * One screen, no scroll: a fixed header, a fixed button row, and the two gauges
 * absorbing every pixel in between. Session detail that used to sit under the
 * gauges moved to [HelpScreen]; the fault codes moved to [DiagnosticsScreen].
 */
@Composable fun InstrumentsScreen(
    state: MonitorState, link: BluetoothLink, onConnect: () -> Unit,
    onDemo: () -> Unit, onDisconnect: () -> Unit, modifier: Modifier = Modifier
) {
    val t = state.telemetry
    BoxWithConstraints(modifier.fillMaxSize().background(Primer.BgDefault)) {
        val wide = maxWidth >= 680.dp
        Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {

            ScreenHeader("Tracker / Instrumentos", "TOBD Monitor",
                if (state.phase == ConnectionPhase.DEMO) "Simulador local • sem veículo"
                else "Bluetooth ${linkLabel(link)} • ${state.deviceName ?: "nenhum dispositivo"}",
                trailing = { ConnectionStatus(state.phase) })

            if (state.phase == ConnectionPhase.DEMO)
                Banner("Modo demonstração: os valores são simulados.", Tone.ATTENTION, maxLines = 1)

            // In demo the repository's message repeats the banner word for word;
            // showing both cost a line of height and said nothing new.
            if (state.phase != ConnectionPhase.DEMO) state.message?.let {
                if (state.phase == ConnectionPhase.ERROR) Banner(it, Tone.DANGER)
                else Text(it, color = Primer.FgMuted, fontSize = 12.sp, maxLines = 2)
            }

            if (wide) Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                SpeedGauge(t.speed, t.nowMs, state.live, Modifier.weight(1f).fillMaxHeight())
                RpmGauge(t.rpm, t.nowMs, state.live, Modifier.weight(1f).fillMaxHeight())
            } else {
                SpeedGauge(t.speed, t.nowMs, state.live, Modifier.fillMaxWidth().weight(1f))
                RpmGauge(t.rpm, t.nowMs, state.live, Modifier.fillMaxWidth().weight(1f))
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (state.active) PrimerButton(
                    if (state.phase == ConnectionPhase.CONNECTING) "Cancelar conexão" else "Desconectar",
                    onDisconnect, Modifier.weight(1f), ButtonVariant.DANGER)
                else {
                    PrimerButton("Modo demo", onDemo, Modifier.weight(1f))
                    PrimerButton("Conectar ELM327", onConnect, Modifier.weight(1f), ButtonVariant.PRIMARY)
                }
            }
        }
    }
}

private fun linkLabel(link: BluetoothLink) = when (link) {
    BluetoothLink.CONNECTED -> "conectado"
    BluetoothLink.CONNECTING -> "conectando"
    else -> "desconectado"
}
