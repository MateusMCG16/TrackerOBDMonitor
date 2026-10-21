package br.com.tracker.obd.obd

import br.com.tracker.obd.data.*
import br.com.tracker.obd.demo.DemoTelemetrySource
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.IOException

data class QueryTrace(val sensor: Sensor, val atMs: Long, val latencyMs: Long, val valid: Boolean, val detail: String?)

fun interface TransportConnector { suspend fun connect(address: String): ElmTransport }

class ObdRepository(
    private val scope: CoroutineScope, private val connector: TransportConnector,
    private val clock: MonotonicClock = SystemMonotonicClock
) {
    private val mutable = MutableStateFlow(MonitorState())
    val state: StateFlow<MonitorState> = mutable.asStateFlow()
    private val mutableTrace = MutableStateFlow<List<QueryTrace>>(emptyList())
    val queryTrace: StateFlow<List<QueryTrace>> = mutableTrace.asStateFlow()
    private var job: Job? = null
    @Volatile private var active: Elm327Client? = null
    private var meters = Sensor.entries.associateWith { FrequencyMeter() }
    private val ticker = scope.launch { while (isActive) { tick(); delay(100) } }

    private fun replace(initial: MonitorState, work: suspend CoroutineScope.() -> Unit) {
        val old = job
        old?.cancel()
        active?.close()
        active = null
        mutable.value = initial
        mutableTrace.value = emptyList()
        meters = Sensor.entries.associateWith { FrequencyMeter() }
        job = scope.launch {
            old?.cancelAndJoin()
            work()
        }
    }
    fun connect(address: String, name: String) = replace(
        MonitorState(phase = ConnectionPhase.CONNECTING, deviceName = name)
    ) {
        for (attempt in 0..3) {
            ensureActive()
            mutable.update { it.copy(phase = ConnectionPhase.CONNECTING, attempt = attempt, message =
                if (attempt == 0) "Abrindo conexão Bluetooth…" else "Reconexão $attempt/3…") }
            var client: Elm327Client? = null
            try {
                client = Elm327Client(connector.connect(address))
                active = client
                mutable.update { it.copy(phase = ConnectionPhase.CONNECTED, message = "Configurando ELM327…") }
                val warnings = client.initialize()
                mutable.update { it.copy(phase = ConnectionPhase.WAITING, message = "Verificando suporte aos PIDs…", warnings = warnings) }
                val supported = ObdPidParser.supported(client.execute(ElmCommand.SUPPORTED))
                if (supported.isEmpty()) throw ElmProtocolException("Velocidade e RPM não anunciados pelo veículo.")
                for (sensor in Sensor.entries.filter { it !in supported }) {
                    mutable.update { state -> state.copy(telemetry = state.telemetry.withSensor(sensor,
                        SensorTelemetry(reading = Reading(issue = "PID não suportado")))) }
                }
                mutable.update { it.copy(message = "Aguardando leituras válidas…") }
                val session = client
                ObdPollingScheduler(clock).run(supported, { sensor ->
                    val started = clock.nowMs()
                    try {
                        ObdPidParser.parse(sensor, session.execute(if (sensor == Sensor.RPM) ElmCommand.RPM else ElmCommand.SPEED))
                    } catch (e: IOException) {
                        val finished = clock.nowMs()
                        record(sensor, PidResult.Missing(e.message ?: "Erro de comunicação"), finished,
                            finished - started, mutable.value.telemetry.slotMs)
                        throw e
                    }
                }) { sample ->
                    record(sample.sensor, sample.result, sample.finishedMs, sample.latencyMs, sample.slotMs)
                }
                return@replace
            } catch (e: CancellationException) { throw e }
              catch (e: SecurityException) {
                mutable.update { it.copy(phase = ConnectionPhase.ERROR, message = "Permissão Bluetooth negada ou revogada.") }
                return@replace
            } catch (e: Exception) {
                val retry = e is IOException && e !is ElmProtocolException && attempt < 3
                mutable.update { it.copy(phase = if (retry) ConnectionPhase.CONNECTING else ConnectionPhase.ERROR,
                    message = e.message ?: "Falha de comunicação Bluetooth", attempt = attempt) }
                if (!retry) return@replace
            } finally { client?.close(); if (active === client) active = null }
            delay(1000L shl attempt)
        }
    }
    fun demo() = replace(MonitorState(phase = ConnectionPhase.DEMO, deviceName = "Simulador local",
        message = "MODO DEMONSTRAÇÃO • dados simulados")) {
        DemoTelemetrySource(clock).run { sensor, value, now -> record(sensor, PidResult.Value(value), now, 0, 100) }
    }
    private fun record(sensor: Sensor, result: PidResult, now: Long, latency: Long, slot: Long) {
        mutableTrace.update { (it + QueryTrace(sensor, now, latency, result is PidResult.Value, (result as? PidResult.Missing)?.reason)).takeLast(120) }
        if (result is PidResult.Value) meters.getValue(sensor).sample(now)
        mutable.update { state ->
            val old = state.telemetry.sensor(sensor)
            val reading = when (result) {
                is PidResult.Value -> Reading(result.value, now, validForMs = (slot * 6).coerceIn(1500L, 5000L))
                is PidResult.Missing -> old.reading.copy(issue = result.reason)
            }
            val data = old.copy(reading = reading, latencyMs = latency, queries = old.queries + 1,
                validSamples = old.validSamples + if (result is PidResult.Value) 1 else 0,
                hz = meters.getValue(sensor).hz(now))
            state.copy(phase = if (state.phase == ConnectionPhase.DEMO) ConnectionPhase.DEMO
                else if (result is PidResult.Value) ConnectionPhase.RECEIVING else state.phase,
                telemetry = state.telemetry.withSensor(sensor, data).copy(slotMs = slot, nowMs = now),
                message = if (state.phase == ConnectionPhase.DEMO) state.message
                    else if (result is PidResult.Value) null else (result as PidResult.Missing).reason)
        }
    }
    private fun tick() {
        val now = clock.nowMs()
        mutable.update { state ->
            val t = state.telemetry
            val anyFresh = Sensor.entries.any { t.sensor(it).reading.availability(now) == Availability.VALID }
            state.copy(telemetry = t.copy(nowMs = now, rpm = t.rpm.copy(hz = meters.getValue(Sensor.RPM).hz(now)),
                speed = t.speed.copy(hz = meters.getValue(Sensor.SPEED).hz(now))),
                phase = if (state.phase == ConnectionPhase.RECEIVING && !anyFresh) ConnectionPhase.WAITING else state.phase)
        }
    }
    fun disconnect() {
        job?.cancel() // Keep the canceled job so a subsequent connection can join its cleanup.
        active?.close(); active = null
        meters = Sensor.entries.associateWith { FrequencyMeter() }
        // Preserve measured values with their original timestamps; they age naturally.
        mutable.update { if (it.phase == ConnectionPhase.DEMO) MonitorState()
            else it.copy(phase = ConnectionPhase.DISCONNECTED, message = null, attempt = 0) }
    }
    fun close() { disconnect(); ticker.cancel() }
}
