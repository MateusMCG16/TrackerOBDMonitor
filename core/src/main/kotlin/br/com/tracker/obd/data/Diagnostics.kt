package br.com.tracker.obd.data

/** Mode 01 PID 01: the lamp state and how many codes the modules claim to hold. */
data class DtcStatus(val milOn: Boolean, val count: Int)

data class DtcCode(val code: String, val description: String?, val origin: String)

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
