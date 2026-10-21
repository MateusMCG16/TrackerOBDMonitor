package br.com.tracker.obd.obd

/** Deliberately closed allowlist: no public arbitrary command / CAN terminal. */
enum class ElmCommand(val wire: String, val timeoutMs: Long = 2000) {
    RESET("ATZ", 5000), ECHO_OFF("ATE0"), LINEFEEDS_OFF("ATL0"), SPACES_OFF("ATS0"),
    HEADERS_OFF("ATH0"), AUTO_PROTOCOL("ATSP0"), SUPPORTED("0100", 15000), RPM("010C"), SPEED("010D")
}
interface ElmTransport {
    suspend fun write(bytes: ByteArray)
    suspend fun readByte(): Int
    fun close()
}
class ElmTimeoutException : java.io.IOException("ELM327 não respondeu com > dentro do prazo; sessão encerrada.")
class ElmProtocolException(message: String) : java.io.IOException(message)
