package br.com.tracker.obd.obd

import br.com.tracker.obd.data.Sensor

sealed interface PidResult {
    data class Value(val value: Double) : PidResult
    data class Missing(val reason: String) : PidResult
}
object ObdPidParser {
    private const val HEX = "0123456789ABCDEF"
    /** Also drops the stray NUL bytes the ELM327 may insert into the stream (datasheet, p. 9). */
    fun lines(raw: String): List<String> = raw.uppercase().replace(">", "\r")
        .split('\r', '\n').map { line -> line.filterNot { it.isWhitespace() || it == '\u0000' } }
        .filter { it.isNotEmpty() }
    fun error(raw: String): String? = lines(raw).firstOrNull { line ->
        line == "?" || listOf("NODATA", "STOPPED", "UNABLETOCONNECT", "CANERROR", "BUSERROR",
            "BUSINIT:ERROR", "BUFFERFULL", "LVRESET", "ERROR").any { it in line }
    }
    private fun String.isHexFrame() = isNotEmpty() && length % 2 == 0 && all { it in HEX }
    private fun frames(raw: String, prefix: String, bytes: Int): List<String> = lines(raw).mapNotNull { line ->
        // SEARCHING... may share the first response line with some clones.
        val candidate = line.removePrefix("SEARCHING...")
        candidate.takeIf { it.startsWith(prefix) && it.length >= prefix.length + bytes * 2 && it.isHexFrame() }
    }
    /**
     * The data bytes of a mode 01 reply, reassembling the ISO 15765 multiline form
     * (a length line, then 0: / 1: … frames) that a multi-PID request can produce.
     */
    private fun payload(raw: String): String? {
        val lines = lines(raw).map { it.removePrefix("SEARCHING...") }.filter { it.isNotEmpty() }
        val ordered = lines.filter { it.length > 2 && it[1] == ':' && it[0] in HEX }
        if (ordered.isEmpty()) return lines.firstOrNull { it.startsWith("41") && it.isHexFrame() }
        val joined = ordered.sortedBy { HEX.indexOf(it[0]) }.joinToString("") { it.substring(2) }
        val declared = lines.firstOrNull { it.length <= 3 && it.all { c -> c in HEX } }?.toInt(16)
        val body = if (declared != null && joined.length >= declared * 2) joined.take(declared * 2) else joined
        return body.takeIf { it.startsWith("41") && it.isHexFrame() }
    }
    /** Walks a mode 01 reply PID by PID; one reply may carry several of them. */
    private fun values(raw: String): Map<Sensor, Double> {
        val body = payload(raw) ?: return emptyMap()
        val values = mutableMapOf<Sensor, Double>()
        var at = 2 // Past the 41 mode byte.
        while (at + 2 <= body.length) {
            val pid = body.substring(at, at + 2).toInt(16)
            // An unknown PID has an unknown width, so the rest can no longer be trusted.
            val sensor = Sensor.entries.firstOrNull { it.pid == pid } ?: break
            val width = if (sensor == Sensor.RPM) 2 else 1
            if (at + 2 + width * 2 > body.length) break
            val a = body.substring(at + 2, at + 4).toInt(16)
            values.putIfAbsent(sensor,
                if (sensor == Sensor.RPM) (a * 256 + body.substring(at + 4, at + 6).toInt(16)) / 4.0 else a.toDouble())
            at += 2 + width * 2
        }
        return values
    }
    fun parse(sensor: Sensor, raw: String): PidResult = parseAll(raw, listOf(sensor)).getValue(sensor)
    fun parseAll(raw: String, sensors: List<Sensor>): Map<Sensor, PidResult> {
        error(raw)?.let { reason -> return sensors.associateWith { PidResult.Missing(reason) } }
        val values = values(raw)
        return sensors.associateWith { sensor ->
            values[sensor]?.let { PidResult.Value(it) }
                ?: PidResult.Missing("Resposta sem PID válido ${sensor.command}")
        }
    }
    fun supported(raw: String): Set<Sensor> {
        error(raw)?.let { throw ElmProtocolException("Consulta 0100 falhou: $it") }
        val frames = frames(raw, "4100", 4)
        if (frames.isEmpty()) throw ElmProtocolException("Não foi possível verificar os PIDs suportados (0100).")
        val mask = frames.fold(0L) { acc, f -> acc or f.substring(4, 12).toLong(16) }
        return Sensor.entries.filter { mask and (1L shl (32 - it.pid)) != 0L }.toSet()
    }
    /** ATDPN answers e.g. '6' or, while the protocol is automatic, 'A6'. CAN is 6…C. */
    fun isCanProtocol(raw: String): Boolean {
        val answer = lines(raw).firstOrNull { it != ElmCommand.PROTOCOL_NUMBER.wire } ?: return false
        val digit = answer.removePrefix("A").firstOrNull() ?: return false
        return digit in "6789ABC"
    }
}
