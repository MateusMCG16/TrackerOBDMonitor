package br.com.tracker.obd.data

/**
 * Everything the detail screens show besides speed and RPM. A value is always a
 * Double; [RUN_TIME] is in seconds and [FUEL_SYSTEM] is the raw SAE status code,
 * and it is the screen's job to turn those two into words.
 */
enum class Signal(val label: String, val unit: String, val decimals: Int = 0) {
    ENGINE_LOAD("Carga calculada", "%"),
    ABSOLUTE_LOAD("Carga absoluta", "%"),
    TIMING_ADVANCE("Avanço de ignição", "°", 1),
    THROTTLE("Borboleta", "%"),
    THROTTLE_COMMANDED("Borboleta comandada", "%"),
    THROTTLE_RELATIVE("Borboleta relativa", "%"),
    PEDAL_D("Pedal D", "%"),
    PEDAL_E("Pedal E", "%"),
    RUN_TIME("Tempo desde a partida", "min"),

    MANIFOLD_PRESSURE("Pressão do coletor", "kPa", 1),
    BAROMETRIC("Pressão barométrica", "kPa"),
    AIR_FLOW("Fluxo de ar", "g/s", 2),
    FUEL_TRIM_SHORT("Trim curto", "%", 1),
    FUEL_TRIM_LONG("Trim longo", "%", 1),
    LAMBDA("Lambda comandada", "", 3),
    FUEL_PRESSURE("Pressão de combustível", "kPa"),
    FUEL_LEVEL("Nível do tanque", "%"),
    ETHANOL("Etanol no combustível", "%"),
    PURGE("Purga do cânister", "%"),
    FUEL_SYSTEM("Sistema de combustível", ""),

    COOLANT("Arrefecimento", "°C"),
    INTAKE_AIR("Ar de admissão", "°C"),
    INTAKE_AIR_2("Ar de admissão 2", "°C"),
    AMBIENT("Ar ambiente", "°C"),
    CATALYST("Catalisador", "°C"),

    O2_UPSTREAM("Sonda antes do catalisador", "V", 2),
    O2_DOWNSTREAM("Sonda depois do catalisador", "V", 2),
    ECM_VOLTAGE("Tensão no motor (ECM)", "V", 2),
    TCM_VOLTAGE("Tensão no câmbio (TCM)", "V", 2),
    ADAPTER_VOLTAGE("Tensão no adaptador", "V", 1),

    DISTANCE_SINCE_CLEAR("Distância percorrida", "km"),
    WARMUPS("Aquecimentos", ""),
    DISTANCE_WITH_MIL("Distância com a luz acesa", "km")
}

/**
 * What the screen in front of the user needs. The repository reads only these, so
 * the instruments screen keeps its 5 Hz and a detail screen refreshes its own
 * values instead of sharing the link with screens nobody is looking at.
 */
enum class DataGroup { MOTOR, MIXTURE, TEMPERATURE, ELECTRIC, HISTORY, VEHICLE }

data class ExtraTelemetry(
    val readings: Map<Signal, Reading> = emptyMap(),
    /** Signals whose PID the vehicle does not announce in its support masks. */
    val unsupported: Set<Signal> = emptySet(),
    /** Why the detail screens stay empty, when they do (e.g. a non-CAN protocol). */
    val notice: String? = null
) {
    fun reading(signal: Signal) = readings[signal] ?: Reading()
}

data class VehicleInfo(
    val vin: String? = null, val protocol: String? = null,
    val adapter: String? = null, val modules: Int? = null
)
