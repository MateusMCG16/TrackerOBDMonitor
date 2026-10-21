package br.com.tracker.obd.obd

import br.com.tracker.obd.data.Sensor

sealed interface PidResult {
    data class Value(val value: Double) : PidResult
    data class Missing(val reason: String) : PidResult
}
object ObdPidParser {
    fun lines(raw: String): List<String> = raw.uppercase().replace(">", "\r")
        .split('\r', '\n').map { it.filterNot(Char::isWhitespace) }.filter { it.isNotBlank() }
    fun error(raw: String): String? = lines(raw).firstOrNull { line ->
        line == "?" || listOf("NODATA", "STOPPED", "UNABLETOCONNECT", "CANERROR", "BUSERROR",
            "BUSINIT:ERROR", "BUFFERFULL", "LVRESET", "ERROR").any { it in line }
    }
    private fun frames(raw: String, prefix: String, bytes: Int): List<String> = lines(raw).mapNotNull { line ->
        // SEARCHING... may share the first response line with some clones.
        val candidate = line.removePrefix("SEARCHING...")
        candidate.takeIf { it.startsWith(prefix) && it.length >= prefix.length + bytes * 2 &&
            it.length % 2 == 0 && it.all { c -> c in "0123456789ABCDEF" } }
    }
    fun parse(sensor: Sensor, raw: String): PidResult {
        error(raw)?.let { return PidResult.Missing(it) }
        val prefix = if (sensor == Sensor.RPM) "410C" else "410D"
        val frame = frames(raw, prefix, if (sensor == Sensor.RPM) 2 else 1).firstOrNull()
            ?: return PidResult.Missing("Resposta sem PID válido ${sensor.command}")
        val a = frame.substring(4, 6).toInt(16)
        return PidResult.Value(if (sensor == Sensor.RPM) (a * 256 + frame.substring(6, 8).toInt(16)) / 4.0 else a.toDouble())
    }
    fun supported(raw: String): Set<Sensor> {
        error(raw)?.let { throw ElmProtocolException("Consulta 0100 falhou: $it") }
        val frames = frames(raw, "4100", 4)
        if (frames.isEmpty()) throw ElmProtocolException("Não foi possível verificar os PIDs suportados (0100).")
        val mask = frames.fold(0L) { acc, f -> acc or f.substring(4, 12).toLong(16) }
        return Sensor.entries.filter { mask and (1L shl (32 - it.pid)) != 0L }.toSet()
    }
}
