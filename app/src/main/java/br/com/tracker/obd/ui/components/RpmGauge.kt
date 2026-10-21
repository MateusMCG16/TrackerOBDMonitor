package br.com.tracker.obd.ui.components
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import br.com.tracker.obd.data.SensorTelemetry
@Composable fun RpmGauge(data: SensorTelemetry, now: Long, live: Boolean, modifier: Modifier = Modifier) =
    InstrumentGauge("Rotação do motor", "RPM", 8000f, data, now, live, modifier, rpm = true)
