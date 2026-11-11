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
    private val redlineStore = br.com.tracker.obd.settings.RedlineStore(application)
    val state = repository.state
    val redline = redlineStore.setting
    fun saveRedline(setting: br.com.tracker.obd.data.RedlineSetting) = redlineStore.save(setting)
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
    fun readDtc() = repository.requestDtcRead()
    /** The screen on display decides which detail values are read. */
    fun focus(groups: Set<br.com.tracker.obd.data.DataGroup>) = repository.focus(groups)
    fun disconnect() = repository.disconnect()
    fun pause() = repository.disconnect()
    override fun onCleared() { repository.close(); bluetooth.close() }
}
