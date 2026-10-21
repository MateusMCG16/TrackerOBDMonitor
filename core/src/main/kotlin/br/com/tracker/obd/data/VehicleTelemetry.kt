package br.com.tracker.obd.data

import kotlin.math.max

fun interface MonotonicClock { fun nowMs(): Long }
object SystemMonotonicClock : MonotonicClock { override fun nowMs() = System.nanoTime() / 1_000_000L }
enum class Sensor(val command: String, val pid: Int) { RPM("010C", 12), SPEED("010D", 13) }
enum class Availability { UNAVAILABLE, VALID, STALE }
data class Reading(val value: Double? = null, val measuredAtMs: Long? = null, val issue: String? = null, val validForMs: Long = 1500) {
    fun availability(nowMs: Long, staleAfterMs: Long = validForMs): Availability = when {
        value == null || measuredAtMs == null -> Availability.UNAVAILABLE
        nowMs - measuredAtMs >= staleAfterMs -> Availability.STALE
        else -> Availability.VALID
    }
}
data class SensorTelemetry(
    val reading: Reading = Reading(), val hz: Double = 0.0, val latencyMs: Long? = null,
    val queries: Long = 0, val validSamples: Long = 0
)
data class VehicleTelemetry(
    val speed: SensorTelemetry = SensorTelemetry(), val rpm: SensorTelemetry = SensorTelemetry(),
    val slotMs: Long = 100, val nowMs: Long = 0
) {
    val staleAfterMs: Long get() = max(1500, (slotMs * 6).coerceAtMost(5000))
    fun sensor(sensor: Sensor) = if (sensor == Sensor.RPM) rpm else speed
    fun withSensor(sensor: Sensor, data: SensorTelemetry) =
        if (sensor == Sensor.RPM) copy(rpm = data) else copy(speed = data)
}
enum class ConnectionPhase { DISCONNECTED, CONNECTING, CONNECTED, WAITING, RECEIVING, ERROR, DEMO }
data class MonitorState(
    val phase: ConnectionPhase = ConnectionPhase.DISCONNECTED,
    val deviceName: String? = null, val message: String? = null, val attempt: Int = 0,
    val telemetry: VehicleTelemetry = VehicleTelemetry(), val warnings: List<String> = emptyList()
)

/** Counts only actual successful responses in a rolling two-second window. */
class FrequencyMeter(private val windowMs: Long = 2000) {
    private val timestamps = ArrayDeque<Long>()
    fun sample(nowMs: Long) { timestamps.addLast(nowMs); trim(nowMs) }
    private fun trim(nowMs: Long) { while (timestamps.isNotEmpty() && nowMs - timestamps.first() > windowMs) timestamps.removeFirst() }
    fun hz(nowMs: Long): Double {
        trim(nowMs)
        if (timestamps.size < 2) return 0.0
        val elapsed = nowMs - timestamps.first()
        return if (elapsed > 0) (timestamps.size - 1) * 1000.0 / elapsed else 0.0
    }
}
