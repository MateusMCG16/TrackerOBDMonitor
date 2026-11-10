package br.com.tracker.obd.ui.components

import androidx.compose.runtime.Composable
import br.com.tracker.obd.data.ConnectionPhase
import br.com.tracker.obd.ui.theme.*

fun ConnectionPhase.label() = when(this) {
    ConnectionPhase.DISCONNECTED -> "Desconectado"
    ConnectionPhase.CONNECTING -> "Conectando"
    ConnectionPhase.CONNECTED -> "Conectado"
    ConnectionPhase.WAITING -> "Aguardando dados"
    ConnectionPhase.RECEIVING -> "Recebendo dados"
    ConnectionPhase.ERROR -> "Erro de conexão"
    ConnectionPhase.DEMO -> "Modo demonstração"
}
@Composable fun ConnectionStatus(phase: ConnectionPhase) {
    val tone = when (phase) {
        ConnectionPhase.RECEIVING, ConnectionPhase.CONNECTED -> Tone.SUCCESS
        ConnectionPhase.ERROR -> Tone.DANGER
        ConnectionPhase.CONNECTING, ConnectionPhase.WAITING, ConnectionPhase.DEMO -> Tone.ATTENTION
        else -> Tone.NEUTRAL
    }
    StateLabel(if (phase == ConnectionPhase.DEMO) "Demonstração" else phase.label(), tone)
}
