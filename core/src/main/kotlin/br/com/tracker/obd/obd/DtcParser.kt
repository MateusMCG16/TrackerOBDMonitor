package br.com.tracker.obd.obd

import br.com.tracker.obd.data.DtcStatus
import java.util.Locale

/**
 * Mode 01 PID 01 and mode 03, as described in the ELM327 datasheet (pp. 35-36).
 *
 * Headers stay off, so codes from several modules arrive as separate 43 lines with
 * nothing to tell them apart: the result is one flat list. Attributing a code to a
 * module needs AT H1 and the third header byte, which this parser does not do.
 */
object DtcParser {
    private const val HEX = "0123456789ABCDEF"

    /**
     * The byte after 4101 does double duty: the top bit is the malfunction indicator
     * lamp, the low seven bits are the number of stored codes. Each module answers for
     * itself, so the counts add up and any lit lamp lights the report.
     */
    fun status(raw: String): DtcStatus {
        ObdPidParser.error(raw)?.let { throw ElmProtocolException("Consulta 0101 falhou: $it") }
        val bodies = ObdPidParser.bodies(raw, "4101").filter { it.length >= 6 }
        if (bodies.isEmpty()) throw ElmProtocolException("Resposta inválida ao 0101; estado da MIL desconhecido.")
        var milOn = false
        var count = 0
        for (body in bodies) {
            val byte = body.substring(4, 6).toInt(16)
            if (byte and 0x80 != 0) milOn = true
            count += byte and 0x7F
        }
        return DtcStatus(milOn, count)
    }

    /**
     * The codes in a mode 03 reply. On CAN the byte right after 43 says how many codes
     * follow; the other protocols put the first code's high byte there instead and pad
     * the rest of the frame with 00 (datasheet, p. 36), so [can] decides how to read it.
     *
     * An empty list is a legitimate answer: it means no stored codes.
     */
    fun codes(raw: String, can: Boolean): List<String> {
        // NO DATA to a mode 03 request is 'nothing stored', not a failure.
        val codes = mutableListOf<String>()
        for (body in ObdPidParser.bodies(raw, "43")) {
            var data = body.substring(2)
            if (can) {
                if (data.length < 2) continue
                val declared = data.substring(0, 2).toInt(16)
                data = data.substring(2)
                // Trust the shorter of the two: a lying count must not read past the frame.
                if (data.length > declared * 4) data = data.take(declared * 4)
            }
            var at = 0
            while (at + 4 <= data.length) {
                decode(data.substring(at, at + 4))?.let { if (it !in codes) codes += it }
                at += 4
            }
        }
        return codes
    }

    /**
     * Two bytes to a code: the top two bits pick the letter, the next two the first
     * digit, the remaining three nibbles are the rest. 0000 is the SAE padding and
     * never a real code.
     */
    internal fun decode(hex: String): String? {
        if (hex.length != 4 || hex.any { it !in HEX }) return null
        val value = hex.toInt(16)
        if (value == 0) return null
        return String.format(Locale.ROOT, "%c%d%03X",
            "PCBU"[value shr 14], (value shr 12) and 0b11, value and 0x0FFF)
    }
}
