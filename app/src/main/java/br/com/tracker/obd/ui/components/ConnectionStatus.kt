package br.com.tracker.obd.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.tracker.obd.data.ConnectionPhase
import br.com.tracker.obd.ui.theme.*

fun ConnectionPhase.label() = when(this) {
    ConnectionPhase.DISCONNECTED -> "Desconectado"
    ConnectionPhase.CONNECTING -> "Conectando"
    ConnectionPhase.CONNECTED -> "Conectado"
    ConnectionPhase.WAITING -> "Aguardando dados"
    ConnectionPhase.RECEIVING -> "Recebendo dados"
    ConnectionPhase.ERROR -> "Erro de conexão"
    ConnectionPhase.DEMO -> "MODO DEMONSTRAÇÃO"
}
@Composable fun ConnectionStatus(phase: ConnectionPhase) {
    val color = when (phase) {
        ConnectionPhase.RECEIVING -> Cyan
        ConnectionPhase.ERROR -> Alert
        ConnectionPhase.CONNECTING, ConnectionPhase.WAITING, ConnectionPhase.DEMO -> Warning
        else -> Muted
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        Text(phase.label(), color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}
