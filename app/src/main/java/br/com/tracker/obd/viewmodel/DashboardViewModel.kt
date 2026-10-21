package br.com.tracker.obd.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import br.com.tracker.obd.bluetooth.*
import br.com.tracker.obd.obd.*
import kotlinx.coroutines.flow.*

class DashboardViewModel(application: Application) : AndroidViewModel(application) {
    private val bluetooth = BluetoothConnectionManager(application)
    private val repository = ObdRepository(viewModelScope, TransportConnector { bluetooth.connect(it) })
    val state = repository.state
    val bluetoothLink = bluetooth.link
    private val mutableDevices = MutableStateFlow<List<PairedDevice>>(emptyList())
    val devices = mutableDevices.asStateFlow()
    private val mutableError = MutableStateFlow<String?>(null)
    val deviceError = mutableError.asStateFlow()
    fun refreshDevices() {
        try { mutableDevices.value = bluetooth.pairedDevices(); mutableError.value = null }
        catch (e: Exception) { mutableDevices.value = emptyList(); mutableError.value = e.message ?: "Falha Bluetooth" }
    }
    fun permissionDenied() { mutableError.value = "Permita o acesso a dispositivos próximos nas configurações do aplicativo." }
    fun connect(device: PairedDevice) = repository.connect(device.address, device.name)
    fun demo() = repository.demo()
    fun disconnect() = repository.disconnect()
    fun pause() = repository.disconnect()
    override fun onCleared() { repository.close(); bluetooth.close() }
}
