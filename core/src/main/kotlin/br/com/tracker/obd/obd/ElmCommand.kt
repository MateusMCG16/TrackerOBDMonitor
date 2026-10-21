package br.com.tracker.obd.obd

import br.com.tracker.obd.data.Sensor

/** Deliberately closed allowlist: no public arbitrary command / CAN terminal. */
enum class ElmCommand(val wire: String, val timeoutMs: Long = 2000) {
    RESET("ATZ", 5000), ECHO_OFF("ATE0"), LINEFEEDS_OFF("ATL0"), SPACES_OFF("ATS0"),
    HEADERS_OFF("ATH0"), AUTO_PROTOCOL("ATSP0"), PROTOCOL_NUMBER("ATDPN"),
    SUPPORTED("0100", 15000),
    // A 'response pending' reply (7F xx 78) makes the ELM327 wait up to 5 s on its own,
    // and another 5 s for each further one, so a shorter deadline would kill a healthy
    // session while the adapter is still legitimately waiting (datasheet, p. 47).
    RPM("010C", 6000), SPEED("010D", 6000), BOTH("010C0D", 6000)
}

/** One request on the wire. CAN answers 010C0D in a single message (datasheet, p. 47). */
enum class PollTarget(val command: ElmCommand, val sensors: List<Sensor>) {
    RPM(ElmCommand.RPM, listOf(Sensor.RPM)),
    SPEED(ElmCommand.SPEED, listOf(Sensor.SPEED)),
    BOTH(ElmCommand.BOTH, listOf(Sensor.RPM, Sensor.SPEED))
}

interface ElmTransport {
    suspend fun write(bytes: ByteArray)
    suspend fun readByte(): Int
    fun close()
}
class ElmTimeoutException : java.io.IOException("ELM327 não respondeu com > dentro do prazo; sessão encerrada.")
class ElmProtocolException(message: String) : java.io.IOException(message)
