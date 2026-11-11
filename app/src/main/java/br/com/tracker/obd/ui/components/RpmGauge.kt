package br.com.tracker.obd.ui.components
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import br.com.tracker.obd.data.Redline
import br.com.tracker.obd.data.RedlineSetting
import br.com.tracker.obd.data.SensorTelemetry
@Composable fun RpmGauge(data: SensorTelemetry, now: Long, live: Boolean, redline: RedlineSetting?,
    modifier: Modifier = Modifier) =
    InstrumentGauge("Rotação do motor", "RPM", Redline.scaleFor(redline?.rpm).toFloat(), data, now, live, modifier,
        rpm = true, redline = redline)
