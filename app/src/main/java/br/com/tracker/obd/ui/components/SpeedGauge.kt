package br.com.tracker.obd.ui.components
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import br.com.tracker.obd.data.SensorTelemetry
@Composable fun SpeedGauge(data: SensorTelemetry, now: Long, live: Boolean, modifier: Modifier = Modifier) =
    InstrumentGauge("Velocidade", "km/h", 220f, data, now, live, modifier)
