package br.com.tracker.obd.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import br.com.tracker.obd.obd.ElmTransport
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import java.io.IOException
import java.util.UUID
import kotlin.coroutines.resumeWithException

data class PairedDevice(val name: String, val address: String)
enum class BluetoothLink { DISCONNECTED, CONNECTING, CONNECTED }

/** No discovery: Android 12+ needs CONNECT only; location/SCAN are unnecessary. */
@SuppressLint("MissingPermission")
class BluetoothConnectionManager(context: Context) {
    private val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
    private val mutableLink = MutableStateFlow(BluetoothLink.DISCONNECTED)
    val link: StateFlow<BluetoothLink> = mutableLink.asStateFlow()
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    fun pairedDevices(): List<PairedDevice> {
        val bt = adapter ?: throw IOException("Este aparelho não possui Bluetooth Classic.")
        if (!bt.isEnabled) throw IOException("Ative o Bluetooth do Android e atualize a lista.")
        return bt.bondedDevices.map { PairedDevice(it.name ?: "Dispositivo Bluetooth", it.address) }
            .sortedBy { it.name.lowercase() }
    }
    suspend fun connect(address: String): ElmTransport {
        val bt = adapter ?: throw IOException("Bluetooth indisponível")
        if (!bt.isEnabled) throw IOException("Bluetooth desativado")
        val device = bt.getRemoteDevice(address)
        if (device.bondState != BluetoothDevice.BOND_BONDED) throw IOException("Pareie o ELM327 nas configurações do Android.")
        mutableLink.value = BluetoothLink.CONNECTING
        try {
            // Documented SPP socket APIs only. A few clones require insecure RFCOMM.
            return try { open(device.createRfcommSocketToServiceRecord(SPP)) }
                catch (e: IOException) { open(device.createInsecureRfcommSocketToServiceRecord(SPP)) }
        } catch (e: TimeoutCancellationException) {
            mutableLink.value = BluetoothLink.DISCONNECTED
            throw IOException("Timeout ao conectar ao adaptador Bluetooth", e)
        } catch (e: Exception) { mutableLink.value = BluetoothLink.DISCONNECTED; throw e }
    }
    private suspend fun open(socket: BluetoothSocket): ElmTransport = withTimeout(12_000) {
        suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { runCatching { socket.close() } }
            ioScope.launch {
                try {
                    socket.connect()
                    val session = SocketTransport(socket) { mutableLink.value = BluetoothLink.DISCONNECTED }
                    mutableLink.value = BluetoothLink.CONNECTED
                    continuation.resume(session) { _, value, _ -> value.close() }
                } catch (e: Exception) {
                    runCatching { socket.close() }
                    if (continuation.isActive) continuation.resumeWithException(e)
                }
            }
        }
    }
    fun close() { ioScope.cancel() }
    companion object { val SPP: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB") }
}

private class SocketTransport(private val socket: BluetoothSocket, private val onClose: () -> Unit) : ElmTransport {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val chunks = Channel<ByteArray>(16)
    private var current = byteArrayOf()
    private var offset = 0
    @Volatile private var closed = false
    init {
        scope.launch {
            try {
                val buffer = ByteArray(512)
                while (isActive) {
                    val count = socket.inputStream.read(buffer)
                    if (count < 0) throw java.io.EOFException("ELM327 desconectado")
                    if (count > 0) chunks.send(buffer.copyOf(count))
                }
            } catch (e: Exception) { chunks.close(e) }
        }
    }
    override suspend fun write(bytes: ByteArray): Unit = suspendCancellableCoroutine { continuation ->
        if (closed) { continuation.resumeWithException(IOException("Socket encerrado")); return@suspendCancellableCoroutine }
        continuation.invokeOnCancellation { close() }
        scope.launch {
            try {
                socket.outputStream.write(bytes); socket.outputStream.flush()
                continuation.resume(Unit) { _, _, _ -> }
            } catch (e: Exception) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }
        }
    }
    override suspend fun readByte(): Int {
        if (offset >= current.size) {
            val result = chunks.receiveCatching()
            current = result.getOrNull() ?: throw IOException("Conexão ELM327 encerrada", result.exceptionOrNull())
            offset = 0
        }
        return current[offset++].toInt() and 0xff
    }
    override fun close() {
        if (closed) return
        closed = true
        runCatching { socket.close() }
        chunks.cancel(); scope.cancel(); onClose()
    }
}
