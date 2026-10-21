package br.com.tracker.obd.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.tracker.obd.bluetooth.PairedDevice
import br.com.tracker.obd.ui.theme.*

@Composable fun ConnectionScreen(devices: List<PairedDevice>, error: String?, onSelect: (PairedDevice) -> Unit,
    onRefresh: () -> Unit, onSettings: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text("Conectar adaptador") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Pareie o ELM327 nas configurações do Android. Depois selecione abaixo. Somente Bluetooth Classic SPP.")
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (devices.isEmpty()) Text("Nenhum dispositivo pareado disponível.", color = Muted)
                LazyColumn(Modifier.heightIn(max = 280.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(devices, key = { it.address }) { device ->
                        OutlinedCard(onClick = { onSelect(device) }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp)) {
                                Text(device.name, color = Cyan)
                                Text(device.address, color = Muted, fontSize = 11.sp)
                            }
                        }
                    }
                }
                TextButton(onClick = onSettings) { Text("Abrir configurações Bluetooth") }
            }
        },
        confirmButton = { TextButton(onClick = onRefresh) { Text("Permitir / atualizar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Fechar") } }
    )
}
