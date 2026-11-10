package br.com.tracker.obd.obd

import br.com.tracker.obd.data.*
import br.com.tracker.obd.demo.DemoTelemetrySource
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

data class QueryTrace(val sensor: Sensor, val atMs: Long, val latencyMs: Long, val valid: Boolean, val detail: String?)

fun interface TransportConnector { suspend fun connect(address: String): ElmTransport }

/** Not an IOException: it ends the combined mode, never the Bluetooth session. */
private class CombinedUnsupported : Exception()

/** A detail value is re-read every few seconds at worst; older than this it is shown as stale. */
private const val DETAIL_VALID_MS = 8000L

/** What one Bluetooth session has learned for the detail screens. */
private class Details(val pids: MutableSet<Int>) {
    var masksRead = false
    var can: Boolean? = null
    var identified = false
    var cursor = 0
}

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
    @Volatile private var meters = Sensor.entries.associateWith { FrequencyMeter() }
    private val dtcRequested = AtomicBoolean(false)
    @Volatile private var focus: Set<DataGroup> = emptySet()
    private val ticker = scope.launch { while (isActive) { tick(); delay(100) } }

    private fun replace(initial: MonitorState, work: suspend CoroutineScope.() -> Unit) {
        val old = job
        old?.cancel()
        active?.close()
        active = null
        mutable.value = initial
        mutableTrace.value = emptyList()
        dtcRequested.set(false)
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
                val supportRaw = client.execute(ElmCommand.SUPPORTED)
                val supported = ObdPidParser.supported(supportRaw)
                if (supported.isEmpty()) throw ElmProtocolException("Velocidade e RPM não anunciados pelo veículo.")
                // One 4100 reply per module, so the count is free: no extra request.
                mutable.update { it.copy(vehicle = it.vehicle.copy(adapter = client.identity,
                    modules = ObdPidParser.bodies(supportRaw, "4100").size)) }
                val details = Details(ExtraPids.mask(supportRaw, 0x00).toMutableSet())
                for (sensor in Sensor.entries.filter { it !in supported }) {
                    mutable.update { state -> state.copy(telemetry = state.telemetry.withSensor(sensor,
                        SensorTelemetry(reading = Reading(issue = "PID não suportado")))) }
                }
                mutable.update { it.copy(message = "Aguardando leituras válidas…") }
                val session = client
                poll(session, chooseTargets(session, supported), singleTargets(supported), details)
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
    private fun singleTargets(supported: Set<Sensor>) = Sensor.entries.filter { it in supported }
        .map { if (it == Sensor.RPM) PollTarget.RPM else PollTarget.SPEED }

    /** CAN answers both PIDs in one message, so a cycle costs one transaction instead of two. */
    private suspend fun chooseTargets(client: Elm327Client, supported: Set<Sensor>): List<PollTarget> {
        val single = singleTargets(supported)
        if (single.size < 2) return single
        return if (ObdPidParser.isCanProtocol(client.execute(ElmCommand.PROTOCOL_NUMBER)))
            listOf(PollTarget.BOTH) else single
    }
    private suspend fun poll(session: Elm327Client, preferred: List<PollTarget>, fallback: List<PollTarget>,
        details: Details) {
        var targets = preferred
        while (true) {
            var misses = 0
            try {
                ObdPollingScheduler(clock).run(targets, { target ->
                    val started = clock.nowMs()
                    val results = try {
                        ObdPidParser.parseAll(session.execute(target.command), target.sensors).toList()
                    } catch (e: IOException) {
                        val finished = clock.nowMs()
                        for (sensor in target.sensors) record(sensor, PidResult.Missing(e.message ?: "Erro de comunicação"),
                            finished, finished - started, mutable.value.telemetry.slotMs)
                        throw e
                    }
                    if (results.none { it.second is PidResult.Value }) misses++ else misses = 0
                    // A clone may simply ignore a multi-PID request: step down without dropping the link.
                    if (misses >= 3 && targets.singleOrNull() == PollTarget.BOTH) throw CombinedUnsupported()
                    results
                }, onSample = { sample ->
                    for ((sensor, result) in sample.results)
                        record(sensor, result, sample.finishedMs, sample.latencyMs, sample.slotMs)
                }, betweenCycles = { serveDtcRequest(session); serveDetails(session, details) })
                return
            } catch (e: CombinedUnsupported) {
                targets = fallback
                mutable.update { it.copy(warnings = it.warnings +
                    "Adaptador não respondeu a 010C0D; consultando um PID por vez.") }
            }
        }
    }
    fun demo() = replace(MonitorState(phase = ConnectionPhase.DEMO, deviceName = "Simulador local",
        message = "MODO DEMONSTRAÇÃO • dados simulados",
        vehicle = VehicleInfo("DEMO0000000000000", "Simulado", "Simulador local", 2))) {
        DemoTelemetrySource(clock).run(onExtras = { values, now -> recordDetails(values, now) }) { sensor, value, now ->
            record(sensor, PidResult.Value(value), now, 0, 100)
        }
    }

    /** Tells the repository which screen is showing; an empty set means speed and RPM only. */
    fun focus(groups: Set<DataGroup>) { focus = groups }

    /**
     * One detail transaction per polling cycle, and only for the screen on display.
     * Like the code read, it runs between cycles, so it shares the single ELM327
     * transaction lock and its latency never reaches the slot adaptation.
     */
    private suspend fun serveDetails(session: Elm327Client, details: Details) {
        val wanted = focus
        if (wanted.isEmpty()) return
        if (details.can == null) {
            val raw = session.execute(ElmCommand.PROTOCOL_NUMBER)
            val can = ObdPidParser.isCanProtocol(raw)
            details.can = can
            // Several PIDs per request exist only on CAN, and the verified requests rely on it.
            mutable.update { it.copy(vehicle = it.vehicle.copy(protocol = ExtraPids.protocolName(raw)),
                extras = it.extras.copy(notice = if (can) null
                    else "As telas de dados exigem protocolo CAN; este veículo usa outro.")) }
            return
        }
        if (DataGroup.VEHICLE in wanted && !details.identified) {
            details.identified = true
            val vin = ExtraPids.vin(session.execute(ElmCommand.VIN))
            mutable.update { it.copy(vehicle = it.vehicle.copy(vin = vin)) }
            return
        }
        if (details.can != true) return
        if (!details.masksRead) { readMasks(session, details); return }
        val commands = ExtraQuery.entries
            .filter { query -> query.groups.any { it in wanted } && query.pids.any { it in details.pids } }
            .map { it.command } +
            if (DataGroup.ELECTRIC in wanted) listOf(ElmCommand.ADAPTER_VOLTAGE) else emptyList()
        if (commands.isEmpty()) return
        val command = commands[details.cursor++ % commands.size]
        val raw = session.execute(command)
        recordDetails(if (command == ElmCommand.ADAPTER_VOLTAGE)
            ExtraPids.adapterVoltage(raw)?.let { mapOf(Signal.ADAPTER_VOLTAGE to it) }.orEmpty()
            else ExtraPids.readings(raw), clock.nowMs())
    }

    /** 0100 is already known; each further mask is asked only if the previous one announces it. */
    private suspend fun readMasks(session: Elm327Client, details: Details) {
        for ((base, command) in listOf(0x20 to ElmCommand.SUPPORTED_21, 0x40 to ElmCommand.SUPPORTED_41,
            0x60 to ElmCommand.SUPPORTED_61, 0x80 to ElmCommand.SUPPORTED_81)) {
            if (base !in details.pids) break
            details.pids += ExtraPids.mask(session.execute(command), base)
        }
        details.masksRead = true
        val missing = ExtraQuery.entries.flatMap { it.pids }.filter { it !in details.pids }
            .flatMap { ExtraPids.signals(it) }.toSet()
        mutable.update { it.copy(extras = it.extras.copy(unsupported = missing)) }
    }

    private fun recordDetails(values: Map<Signal, Double>, now: Long) {
        if (values.isEmpty()) return
        mutable.update { state -> state.copy(extras = state.extras.copy(readings = state.extras.readings +
            values.mapValues { Reading(it.value, now, validForMs = DETAIL_VALID_MS) })) }
    }

    /**
     * Asks for the stored codes. The read happens in the polling worker, between two
     * cycles, so it shares the single ELM327 transaction lock instead of racing it.
     */
    fun requestDtcRead() {
        if (mutable.value.phase == ConnectionPhase.DEMO) {
            mutable.update { it.copy(dtc = demoReport()) }
            return
        }
        if (active == null || mutable.value.phase !in setOf(ConnectionPhase.WAITING, ConnectionPhase.RECEIVING)) {
            mutable.update { it.copy(dtc = DtcReport(DtcPhase.FAILED,
                message = "Conecte-se ao adaptador antes de ler os códigos.")) }
            return
        }
        dtcRequested.set(true)
        mutable.update { it.copy(dtc = it.dtc.copy(phase = DtcPhase.READING, message = null)) }
    }

    private suspend fun serveDtcRequest(session: Elm327Client) {
        if (!dtcRequested.getAndSet(false)) return
        try {
            val status = DtcParser.status(session.execute(ElmCommand.DTC_STATUS))
            val can = ObdPidParser.isCanProtocol(session.execute(ElmCommand.PROTOCOL_NUMBER))
            // Confirmed first: a code that is also pending or permanent is listed once, as confirmed.
            val codes = mutableListOf<DtcCode>()
            for ((kind, command, reply) in listOf(
                Triple(DtcKind.STORED, ElmCommand.DTC_STORED, "43"),
                Triple(DtcKind.PENDING, ElmCommand.DTC_PENDING, "47"),
                Triple(DtcKind.PERMANENT, ElmCommand.DTC_PERMANENT, "4A"))) {
                for (code in DtcParser.codes(session.execute(command), can, reply))
                    if (codes.none { it.code == code })
                        codes += DtcCode(code, DtcDescriptions.of(code), DtcDescriptions.origin(code), kind)
            }
            // The count is the sum over the modules that answered 0101; fewer codes than
            // that means something withheld its list, which is worth saying out loud.
            val stored = codes.count { it.kind == DtcKind.STORED }
            val note = when {
                status.count > 0 && stored == 0 ->
                    "A ECU informou ${status.count} código(s), mas não devolveu a lista. O adaptador pode não atender ao modo 03."
                stored < status.count ->
                    "A ECU contabilizou ${status.count} código(s) e devolveu $stored."
                else -> null
            }
            mutable.update { it.copy(dtc = DtcReport(DtcPhase.READY, status, codes, note, clock.nowMs())) }
        } catch (e: CancellationException) { throw e }
          catch (e: ElmProtocolException) {
            // Our own parse refusal: the session is untouched, so keep polling.
            mutable.update { it.copy(dtc = DtcReport(DtcPhase.UNSUPPORTED, message = e.message)) }
        } catch (e: IOException) {
            // execute() already closed the session; let the poll loop see the failure.
            mutable.update { it.copy(dtc = DtcReport(DtcPhase.FAILED,
                message = e.message ?: "Falha de comunicação durante a leitura.")) }
            throw e
        }
    }

    /** Canned codes so the panel can be seen working without a vehicle. */
    private fun demoReport() = DtcReport(DtcPhase.READY, DtcStatus(milOn = true, count = 3),
        listOf("P0133" to DtcKind.STORED, "P0442" to DtcKind.PENDING, "P1631" to DtcKind.STORED)
            .map { (code, kind) -> DtcCode(code, DtcDescriptions.of(code), DtcDescriptions.origin(code), kind) },
        message = "Códigos simulados — nenhuma leitura real foi feita.", readAtMs = clock.nowMs())
    private fun record(sensor: Sensor, result: PidResult, now: Long, latency: Long, slot: Long) {
        mutableTrace.update { (it + QueryTrace(sensor, now, latency, result is PidResult.Value, (result as? PidResult.Missing)?.reason)).takeLast(120) }
        if (result is PidResult.Value) meters.getValue(sensor).sample(now)
        // Read before update: the CAS lambda may re-run, and hz() mutates the window.
        val measuredHz = meters.getValue(sensor).hz(now)
        mutable.update { state ->
            val old = state.telemetry.sensor(sensor)
            val reading = when (result) {
                is PidResult.Value -> Reading(result.value, now, validForMs = (slot * 6).coerceIn(1500L, 5000L))
                is PidResult.Missing -> old.reading.copy(issue = result.reason)
            }
            val data = old.copy(reading = reading, latencyMs = latency, queries = old.queries + 1,
                validSamples = old.validSamples + if (result is PidResult.Value) 1 else 0,
                hz = measuredHz)
            state.copy(phase = if (state.phase == ConnectionPhase.DEMO) ConnectionPhase.DEMO
                else if (result is PidResult.Value) ConnectionPhase.RECEIVING else state.phase,
                telemetry = state.telemetry.withSensor(sensor, data).copy(slotMs = slot, nowMs = now),
                message = if (state.phase == ConnectionPhase.DEMO) state.message
                    else if (result is PidResult.Value) null else (result as PidResult.Missing).reason)
        }
    }
    private fun tick() {
        val now = clock.nowMs()
        val window = meters
        val rpmHz = window.getValue(Sensor.RPM).hz(now)
        val speedHz = window.getValue(Sensor.SPEED).hz(now)
        mutable.update { state ->
            val t = state.telemetry
            val anyFresh = Sensor.entries.any { t.sensor(it).reading.availability(now) == Availability.VALID }
            state.copy(telemetry = t.copy(nowMs = now, rpm = t.rpm.copy(hz = rpmHz),
                speed = t.speed.copy(hz = speedHz)),
                phase = if (state.phase == ConnectionPhase.RECEIVING && !anyFresh) ConnectionPhase.WAITING else state.phase)
        }
    }
    fun disconnect() {
        job?.cancel() // Keep the canceled job so a subsequent connection can join its cleanup.
        active?.close(); active = null
        meters = Sensor.entries.associateWith { FrequencyMeter() }
        dtcRequested.set(false)
        // Preserve measured values with their original timestamps; they age naturally.
        // A report already read stays on screen with its timestamp; a read in flight dies.
        mutable.update { if (it.phase == ConnectionPhase.DEMO) MonitorState()
            else it.copy(phase = ConnectionPhase.DISCONNECTED, message = null, attempt = 0,
                dtc = if (it.dtc.phase == DtcPhase.READING) DtcReport(DtcPhase.FAILED,
                    message = "Leitura interrompida pela desconexão.") else it.dtc) }
    }
    fun close() { disconnect(); ticker.cancel() }
}
