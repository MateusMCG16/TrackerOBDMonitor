package br.com.tracker.obd.obd

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

class Elm327Client(private val transport: ElmTransport) {
    private val mutex = Mutex()
    private var closed = false

    suspend fun execute(command: ElmCommand): String = mutex.withLock {
        check(!closed) { "Sessão ELM327 encerrada" }
        try {
            withTimeout(command.timeoutMs) {
                transport.write((command.wire + "\r").toByteArray(Charsets.US_ASCII))
                val response = StringBuilder()
                while (true) {
                    val byte = transport.readByte()
                    if (byte < 0) throw java.io.EOFException("Bluetooth desconectado")
                    if (byte == '>'.code) break
                    if (response.length >= 8192) throw ElmProtocolException("Resposta ELM327 excedeu 8 KiB")
                    response.append(byte.toChar())
                }
                response.toString()
            }
        } catch (e: TimeoutCancellationException) {
            close() // A late prompt can never become the next PID's response.
            throw ElmTimeoutException()
        } catch (e: CancellationException) { close(); throw e }
          catch (e: Exception) { close(); throw e }
    }

    suspend fun initialize(): List<String> {
        val warnings = mutableListOf<String>()
        val optional = setOf(ElmCommand.ECHO_OFF, ElmCommand.LINEFEEDS_OFF, ElmCommand.SPACES_OFF)
        for (command in listOf(ElmCommand.RESET, ElmCommand.ECHO_OFF, ElmCommand.LINEFEEDS_OFF,
            ElmCommand.SPACES_OFF, ElmCommand.HEADERS_OFF, ElmCommand.AUTO_PROTOCOL)) {
            val raw = execute(command)
            val lines = ObdPidParser.lines(raw).filter { it != command.wire }
            val accepted = if (command == ElmCommand.RESET) lines.any { "ELM" in it || it == "OK" }
                else lines.any { it == "OK" }
            if (!accepted || ObdPidParser.error(raw) != null) {
                if (command in optional) warnings += "${command.wire} não aceito; parser tolerante ativado."
                else throw ElmProtocolException("${command.wire} não aceito pelo adaptador. Configuração interrompida.")
            }
        }
        return warnings
    }
    fun close() { closed = true; transport.close() }
}
