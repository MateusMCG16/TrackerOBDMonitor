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
    RPM("010C", 6000), SPEED("010D", 6000), BOTH("010C0D", 6000),
    // Read-only diagnostics. Mode 04 (erase codes) is deliberately absent: it writes to
    // the ECU, and SAE requires a scan tool to confirm intent before sending it.
    DTC_STATUS("0101", 10000), DTC_STORED("03", 10000),
    // Modes 07 and 0A are the same read as 03 for the pending and the permanent lists.
    DTC_PENDING("07", 10000), DTC_PERMANENT("0A", 10000),
    // Support masks beyond 0100: each one says which of the next 32 PIDs exist.
    SUPPORTED_21("0120", 15000), SUPPORTED_41("0140", 15000),
    SUPPORTED_61("0160", 15000), SUPPORTED_81("0180", 15000),
    // Live data for the detail screens. Every string below was sent to the vehicle with
    // this very adapter on 2026-11-09 and answered. The grouping is not cosmetic: the
    // v2.1 clone resets itself on a 4-PID request, while 2 and 3 PIDs whose reply fits
    // one CAN frame (six bytes after the 41) are safe. Do not merge these groups.
    LOAD_THROTTLE_TIMING("0104110E", 6000), MAF_PEDAL("011049", 6000), MANIFOLD("0187", 6000),
    TRIMS_THROTTLE("0106074C", 6000), OXYGEN("011415", 6000), LAMBDA("0144", 6000),
    TEMPERATURES("01050F46", 6000), CATALYST_LEVEL("013C2F", 6000), INTAKE_SENSORS("0168", 6000),
    VOLTAGE_ETHANOL("014252", 6000), BARO_PRESSURE_PURGE("01330A2E", 6000), LOADS("014345", 6000),
    PEDAL_WARMUPS("01474A30", 6000), RUNTIME_FUEL_SYSTEM("011F03", 6000), DISTANCES("013121", 6000),
    // 0904 (calibration ID) is deliberately absent: its long reply resets this clone.
    VIN("0902", 10000),
    // Read by the adapter from its own pin 16; nothing is sent to the vehicle.
    ADAPTER_VOLTAGE("ATRV")
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
