package br.com.tracker.obd.obd

import br.com.tracker.obd.data.DataGroup
import br.com.tracker.obd.data.Signal

/** One mode 01 PID: how many data bytes it carries and what they mean. */
internal class PidSpec(
    val pid: Int, val width: Int, val signals: List<Signal>,
    val decode: (IntArray) -> Map<Signal, Double>
)

/**
 * One request for the detail screens and the screens that need it. A request is
 * sent only while one of its [groups] is on screen.
 */
enum class ExtraQuery(val command: ElmCommand, val groups: Set<DataGroup>) {
    LOAD_THROTTLE_TIMING(ElmCommand.LOAD_THROTTLE_TIMING, setOf(DataGroup.MOTOR)),
    MAF_PEDAL(ElmCommand.MAF_PEDAL, setOf(DataGroup.MOTOR, DataGroup.MIXTURE)),
    TRIMS_THROTTLE(ElmCommand.TRIMS_THROTTLE, setOf(DataGroup.MOTOR, DataGroup.MIXTURE)),
    LOADS(ElmCommand.LOADS, setOf(DataGroup.MOTOR)),
    PEDAL_WARMUPS(ElmCommand.PEDAL_WARMUPS, setOf(DataGroup.MOTOR, DataGroup.HISTORY)),
    RUNTIME_FUEL_SYSTEM(ElmCommand.RUNTIME_FUEL_SYSTEM, setOf(DataGroup.MOTOR, DataGroup.MIXTURE)),
    MANIFOLD(ElmCommand.MANIFOLD, setOf(DataGroup.MIXTURE)),
    LAMBDA(ElmCommand.LAMBDA, setOf(DataGroup.MIXTURE)),
    BARO_PRESSURE_PURGE(ElmCommand.BARO_PRESSURE_PURGE, setOf(DataGroup.MIXTURE)),
    CATALYST_LEVEL(ElmCommand.CATALYST_LEVEL, setOf(DataGroup.MIXTURE, DataGroup.TEMPERATURE)),
    VOLTAGE_ETHANOL(ElmCommand.VOLTAGE_ETHANOL, setOf(DataGroup.MIXTURE, DataGroup.ELECTRIC)),
    TEMPERATURES(ElmCommand.TEMPERATURES, setOf(DataGroup.TEMPERATURE)),
    INTAKE_SENSORS(ElmCommand.INTAKE_SENSORS, setOf(DataGroup.TEMPERATURE)),
    OXYGEN(ElmCommand.OXYGEN, setOf(DataGroup.ELECTRIC)),
    DISTANCES(ElmCommand.DISTANCES, setOf(DataGroup.HISTORY));

    /** The PIDs on the wire, read back from the command so the two cannot drift apart. */
    val pids: List<Int> = command.wire.drop(2).chunked(2).map { it.toInt(16) }
}

object ExtraPids {
    private const val MODULE_VOLTAGE = 0x42

    private fun word(d: IntArray, at: Int = 0) = d[at] * 256 + d[at + 1]
    private fun percent(d: IntArray) = d[0] * 100.0 / 255
    private fun one(pid: Int, width: Int, signal: Signal, value: (IntArray) -> Double) =
        PidSpec(pid, width, listOf(signal)) { mapOf(signal to value(it)) }

    internal val specs: Map<Int, PidSpec> = listOf(
        one(0x03, 2, Signal.FUEL_SYSTEM) { it[0].toDouble() },
        one(0x04, 1, Signal.ENGINE_LOAD, ::percent),
        one(0x05, 1, Signal.COOLANT) { it[0] - 40.0 },
        one(0x06, 1, Signal.FUEL_TRIM_SHORT) { (it[0] - 128) * 100.0 / 128 },
        one(0x07, 1, Signal.FUEL_TRIM_LONG) { (it[0] - 128) * 100.0 / 128 },
        one(0x0A, 1, Signal.FUEL_PRESSURE) { it[0] * 3.0 },
        one(0x0E, 1, Signal.TIMING_ADVANCE) { it[0] / 2.0 - 64 },
        one(0x0F, 1, Signal.INTAKE_AIR) { it[0] - 40.0 },
        one(0x10, 2, Signal.AIR_FLOW) { word(it) / 100.0 },
        one(0x11, 1, Signal.THROTTLE, ::percent),
        one(0x14, 2, Signal.O2_UPSTREAM) { it[0] * 0.005 },
        one(0x15, 2, Signal.O2_DOWNSTREAM) { it[0] * 0.005 },
        one(0x1F, 2, Signal.RUN_TIME) { word(it).toDouble() },
        one(0x21, 2, Signal.DISTANCE_WITH_MIL) { word(it).toDouble() },
        one(0x2E, 1, Signal.PURGE, ::percent),
        one(0x2F, 1, Signal.FUEL_LEVEL, ::percent),
        one(0x30, 1, Signal.WARMUPS) { it[0].toDouble() },
        one(0x31, 2, Signal.DISTANCE_SINCE_CLEAR) { word(it).toDouble() },
        one(0x33, 1, Signal.BAROMETRIC) { it[0].toDouble() },
        one(0x3C, 2, Signal.CATALYST) { word(it) / 10.0 - 40 },
        one(MODULE_VOLTAGE, 2, Signal.ECM_VOLTAGE) { word(it) / 1000.0 },
        one(0x43, 2, Signal.ABSOLUTE_LOAD) { word(it) * 100.0 / 255 },
        one(0x44, 2, Signal.LAMBDA) { word(it) * 2.0 / 65536 },
        one(0x45, 1, Signal.THROTTLE_RELATIVE, ::percent),
        one(0x46, 1, Signal.AMBIENT) { it[0] - 40.0 },
        // Throttle position B rides in a verified request but mirrors PID 11: skipped, not shown.
        PidSpec(0x47, 1, emptyList()) { emptyMap() },
        one(0x49, 1, Signal.PEDAL_D, ::percent),
        one(0x4A, 1, Signal.PEDAL_E, ::percent),
        one(0x4C, 1, Signal.THROTTLE_COMMANDED, ::percent),
        one(0x52, 1, Signal.ETHANOL, ::percent),
        // The first byte says which sensors exist; bit 1 is bank 1 sensor 2.
        PidSpec(0x68, 7, listOf(Signal.INTAKE_AIR_2)) {
            if (it[0] and 0x02 != 0) mapOf(Signal.INTAKE_AIR_2 to it[2] - 40.0) else emptyMap()
        },
        // Bit 0 of the first byte: sensor A present, in 1/32 kPa.
        PidSpec(0x87, 5, listOf(Signal.MANIFOLD_PRESSURE)) {
            if (it[0] and 0x01 != 0) mapOf(Signal.MANIFOLD_PRESSURE to word(it, 1) * 0.03125) else emptyMap()
        }
    ).associateBy { it.pid }

    /** The signals a request can deliver, for marking what the vehicle does not support. */
    fun signals(pid: Int): List<Signal> = specs[pid]?.signals.orEmpty() +
        if (pid == MODULE_VOLTAGE) listOf(Signal.TCM_VOLTAGE) else emptyList()

    /** Walks one reply PID by PID. An unknown PID has an unknown width, so the walk stops there. */
    private fun walk(body: String): Map<Int, IntArray> {
        val found = LinkedHashMap<Int, IntArray>()
        var at = 2 // Past the 41 mode byte.
        while (at + 2 <= body.length) {
            val spec = specs[body.substring(at, at + 2).toInt(16)] ?: break
            val end = at + 2 + spec.width * 2
            if (end > body.length) break
            found.putIfAbsent(spec.pid, IntArray(spec.width) { body.substring(at + 2 + it * 2, at + 4 + it * 2).toInt(16) })
            at = end
        }
        return found
    }

    /**
     * Decodes a detail request. Headers are off, so two modules answering the same
     * request are told apart by content: the reply that carries the most requested
     * PIDs is the engine's. A second reply holding only the module voltage is the
     * transmission's — it is the one PID both modules of this vehicle announce.
     */
    fun readings(raw: String): Map<Signal, Double> {
        if (ObdPidParser.error(raw) != null) return emptyMap()
        val replies = ObdPidParser.bodies(raw, "41").map(::walk).filter { it.isNotEmpty() }
        val primary = replies.maxByOrNull { it.size } ?: return emptyMap()
        val values = mutableMapOf<Signal, Double>()
        for ((pid, data) in primary) values += specs.getValue(pid).decode(data)
        val second = replies.firstOrNull { it !== primary && MODULE_VOLTAGE in it }
        if (second != null && MODULE_VOLTAGE in primary)
            values[Signal.TCM_VOLTAGE] = word(second.getValue(MODULE_VOLTAGE)) / 1000.0
        return values
    }

    /** The PIDs a support mask announces, over every module that answered. Empty on any error. */
    fun mask(raw: String, base: Int): Set<Int> {
        if (ObdPidParser.error(raw) != null) return emptySet()
        val prefix = "41%02X".format(base)
        val pids = mutableSetOf<Int>()
        for (body in ObdPidParser.bodies(raw, prefix)) {
            if (body.length < 12) continue
            val bits = body.substring(4, 12).toLong(16)
            for (i in 0 until 32) if (bits and (1L shl (31 - i)) != 0L) pids += base + i + 1
        }
        return pids
    }

    /** ATRV answers like '13.2V'. */
    fun adapterVoltage(raw: String): Double? =
        Regex("""(\d{1,2}\.\d)V""").find(raw.uppercase())?.groupValues?.get(1)?.toDoubleOrNull()

    /** Mode 09 PID 02. Seventeen printable characters or nothing: a partial VIN is worse than none. */
    fun vin(raw: String): String? {
        if (ObdPidParser.error(raw) != null) return null
        val text = ObdPidParser.bodies(raw, "4902").joinToString("") { body ->
            body.drop(6).chunked(2).filter { it.length == 2 }.map { it.toInt(16) }
                .filter { it in 0x20..0x7E }.joinToString("") { it.toChar().toString() }
        }
        return text.takeIf { it.length == 17 }
    }

    /** ATDPN answers e.g. '7' or, while the protocol is automatic, 'A7'. */
    fun protocolName(raw: String): String? {
        val answer = ObdPidParser.lines(raw).firstOrNull { it != ElmCommand.PROTOCOL_NUMBER.wire } ?: return null
        val digit = (if (answer.length > 1) answer.removePrefix("A") else answer).firstOrNull() ?: return null
        return when (digit) {
            '1' -> "SAE J1850 PWM"
            '2' -> "SAE J1850 VPW"
            '3' -> "ISO 9141-2"
            '4' -> "ISO 14230-4 KWP (5 baud)"
            '5' -> "ISO 14230-4 KWP (rápido)"
            '6' -> "CAN 11 bits, 500 kbps"
            '7' -> "CAN 29 bits, 500 kbps"
            '8' -> "CAN 11 bits, 250 kbps"
            '9' -> "CAN 29 bits, 250 kbps"
            'A' -> "SAE J1939"
            else -> null
        }
    }
}
