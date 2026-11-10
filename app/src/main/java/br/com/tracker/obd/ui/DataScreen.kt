package br.com.tracker.obd.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.tracker.obd.data.*
import br.com.tracker.obd.ui.components.label
import br.com.tracker.obd.ui.theme.*
import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow

/**
 * The four detail screens behind one tab. Each topic is one screen: the chip row
 * swaps the whole grid, nothing scrolls. The order inside a topic puts the pair
 * the eye compares side by side, and an odd last value spans the full row.
 */
enum class DataTopic(val chip: String, val title: String, val group: DataGroup, val signals: List<Signal>) {
    MOTOR("Motor", "Motor", DataGroup.MOTOR, listOf(
        Signal.ENGINE_LOAD, Signal.ABSOLUTE_LOAD,
        Signal.THROTTLE, Signal.THROTTLE_COMMANDED,
        Signal.PEDAL_D, Signal.PEDAL_E,
        Signal.THROTTLE_RELATIVE, Signal.TIMING_ADVANCE,
        Signal.RUN_TIME)),
    MIXTURE("Mistura", "Ar e combustível", DataGroup.MIXTURE, listOf(
        Signal.MANIFOLD_PRESSURE, Signal.BAROMETRIC,
        Signal.AIR_FLOW, Signal.LAMBDA,
        Signal.FUEL_TRIM_SHORT, Signal.FUEL_TRIM_LONG,
        Signal.FUEL_PRESSURE, Signal.PURGE,
        Signal.FUEL_LEVEL, Signal.ETHANOL,
        Signal.FUEL_SYSTEM)),
    TEMPERATURE("Temperatura", "Temperaturas", DataGroup.TEMPERATURE, listOf(
        Signal.COOLANT, Signal.CATALYST,
        Signal.INTAKE_AIR, Signal.INTAKE_AIR_2,
        Signal.AMBIENT)),
    ELECTRIC("Elétrica", "Sondas e elétrica", DataGroup.ELECTRIC, listOf(
        Signal.O2_UPSTREAM, Signal.O2_DOWNSTREAM,
        Signal.ECM_VOLTAGE, Signal.TCM_VOLTAGE,
        Signal.ADAPTER_VOLTAGE))
}

@Composable fun DataScreen(
    state: MonitorState, topic: DataTopic, onTopic: (DataTopic) -> Unit, modifier: Modifier = Modifier
) {
    val demo = state.phase == ConnectionPhase.DEMO
    Column(modifier.fillMaxSize().background(Graphite).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {

        ScreenHeader(if (demo) "MODO DEMONSTRAÇÃO • DADOS SIMULADOS" else "TRACKER / DADOS", topic.title,
            "${state.phase.label()} • ${state.deviceName ?: "nenhum dispositivo"}",
            accent = if (demo) Warning else Cyan)

        ChipRow(DataTopic.entries.map { it.chip }, topic.ordinal) { onTopic(DataTopic.entries[it]) }

        // One line, always present, so the grid below never changes height under the eye.
        val notice = state.extras.notice ?: if (state.live) "Atualiza só os valores desta tela."
            else "Sem sessão ativa: conecte em Instrumentos. Valores antigos ficam esmaecidos."
        Text(notice, color = if (state.extras.notice != null || !state.live) Warning else Muted,
            fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)

        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val columns = if (maxWidth >= 680.dp) 4 else 2
            val rows = topic.signals.chunked(columns)
            val gap = 8.dp
            // Every size inside a tile derives from this, so a short screen shrinks the
            // type instead of pushing a row off the bottom.
            val rowHeight = (maxHeight - gap * (rows.size - 1)) / rows.size
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
                rows.forEach { row ->
                    Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(gap)) {
                        row.forEach { signal ->
                            SignalTile(signal, state.extras.reading(signal), signal in state.extras.unsupported,
                                state.telemetry.nowMs, state.live, rowHeight, Modifier.weight(1f).fillMaxHeight())
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun SignalTile(
    signal: Signal, reading: Reading, unsupported: Boolean, nowMs: Long, live: Boolean,
    rowHeight: Dp, modifier: Modifier
) {
    val value = reading.value
    val fresh = live && reading.availability(nowMs) == Availability.VALID
    val (text, unit) = when {
        value != null -> signalText(signal, value)
        unsupported -> "sem suporte" to ""
        else -> "--" to ""
    }
    val wordy = text.length > 7           // "Malha fechada", "sem suporte": words, not a number.
    val stacked = rowHeight >= 62.dp
    // Sizes are set in dp and converted, so the system font scale cannot overflow a tile.
    val density = LocalDensity.current
    fun size(dp: Float) = with(density) { dp.dp.toSp() }
    val number = if (stacked) (rowHeight.value * 0.33f).coerceIn(18f, 34f) else (rowHeight.value * 0.42f).coerceIn(14f, 20f)
    val valueSize = size(if (wordy) number * 0.58f else number)
    val labelSize = size(if (rowHeight >= 80.dp) 12f else 10.5f)
    val valueColor = when {
        value == null -> Muted
        fresh -> Color(0xFFF3FAFF)
        else -> Muted
    }
    val shape = RoundedCornerShape(16.dp)
    val box = modifier.background(Brush.verticalGradient(listOf(Panel, Color(0xFF0F1724))), shape)
        .border(1.dp, Color(0xFF263449), shape)
        .padding(horizontal = 12.dp, vertical = if (stacked) 8.dp else 4.dp)
        .clearAndSetSemantics {
            contentDescription = "${signal.label}: $text $unit" + if (value != null && !fresh) ", desatualizado" else ""
        }

    @Composable fun Value() = Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text, color = valueColor, fontSize = valueSize, fontWeight = if (wordy) FontWeight.Medium else FontWeight.Light,
            maxLines = 1, softWrap = false, modifier = Modifier.alignByBaseline())
        if (unit.isNotEmpty()) Text(unit, color = if (fresh) Cyan else Muted, fontSize = size(11f),
            maxLines = 1, softWrap = false, modifier = Modifier.alignByBaseline())
    }

    if (stacked) Column(box, verticalArrangement = Arrangement.Center) {
        Text(signal.label, color = Muted, fontSize = labelSize, lineHeight = labelSize * 1.2f,
            maxLines = if (rowHeight >= 92.dp) 2 else 1, overflow = TextOverflow.Ellipsis)
        Value()
    } else Row(box, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(signal.label, color = Muted, fontSize = labelSize, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f))
        Value()
    }
}

private val PT_BR: Locale = Locale.forLanguageTag("pt-BR")

/** The value as the screen prints it, and its unit. */
internal fun signalText(signal: Signal, value: Double): Pair<String, String> = when (signal) {
    Signal.RUN_TIME -> value.toLong().let { String.format(Locale.ROOT, "%d:%02d", it / 60, it % 60) } to signal.unit
    Signal.FUEL_SYSTEM -> when (value.toInt()) {
        1 -> "Malha aberta (motor frio)"
        2 -> "Malha fechada"
        4 -> "Malha aberta (carga ou corte)"
        8 -> "Malha aberta (falha)"
        16 -> "Malha fechada (falha na sonda)"
        else -> "Estado ${value.toInt()}"
    } to ""
    else -> {
        // A trim of -0.04 must print as 0,0 and not as -0,0.
        val shown = if (abs(value) < 0.5 * 10.0.pow(-signal.decimals)) 0.0 else value
        String.format(PT_BR, "%.${signal.decimals}f", shown) to signal.unit
    }
}
