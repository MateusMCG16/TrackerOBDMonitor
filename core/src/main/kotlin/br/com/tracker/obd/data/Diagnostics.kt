package br.com.tracker.obd.data

/** Mode 01 PID 01: the lamp state and how many codes the modules claim to hold. */
data class DtcStatus(val milOn: Boolean, val count: Int)

/** Which list a code came from: mode 03, 07 or 0A. */
enum class DtcKind(val label: String) {
    STORED("confirmado"), PENDING("pendente"), PERMANENT("permanente")
}

data class DtcCode(val code: String, val description: String?, val origin: String,
    val kind: DtcKind = DtcKind.STORED)

enum class DtcPhase {
    /** No mode 03 request has been made in this session. */
    NEVER,
    READING,
    READY,
    /** The adapter or the vehicle refused the request; the Bluetooth link is still up. */
    UNSUPPORTED,
    FAILED
}

data class DtcReport(
    val phase: DtcPhase = DtcPhase.NEVER,
    val status: DtcStatus? = null,
    val codes: List<DtcCode> = emptyList(),
    val message: String? = null,
    val readAtMs: Long? = null
)
