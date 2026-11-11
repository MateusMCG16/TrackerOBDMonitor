package br.com.tracker.obd.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import br.com.tracker.obd.data.*
import br.com.tracker.obd.ui.theme.*
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToInt

private enum class RedlineStep { CHOOSE, MEASURE, TYPE }

private val RPM_FORMAT: NumberFormat = NumberFormat.getIntegerInstance(Locale.forLanguageTag("pt-BR"))
internal fun rpmText(rpm: Int) = "${RPM_FORMAT.format(rpm)} rpm"

/**
 * Shown once per launch, right after the first RPM sample of a session. OBD-II
 * has no PID for the rev limit, so the user either revs to the cut while the app
 * follows the samples, or types the value from the manual.
 */
@Composable fun RedlineScreen(
    rpm: Reading, live: Boolean, saved: RedlineSetting?,
    onSave: (RedlineSetting) -> Unit, onDismiss: () -> Unit
) {
    var step by rememberSaveable { mutableStateOf(RedlineStep.CHOOSE) }
    var peak by remember { mutableStateOf(RedlinePeak()) }
    var typed by rememberSaveable { mutableStateOf(saved?.rpm?.toString() ?: "") }
    // Keyed on the sample's timestamp: each reading counts once, however often the screen redraws.
    LaunchedEffect(rpm.measuredAtMs) {
        if (step == RedlineStep.MEASURE && live) rpm.value?.let { peak = peak.add(it) }
    }

    AlertDialog(onDismissRequest = onDismiss,
        // A stray tap beside the dialog mid-measurement must not throw the peak away.
        properties = DialogProperties(dismissOnClickOutside = false),
        containerColor = Primer.BgMuted, shape = Primer.RadiusLarge,
        titleContentColor = Primer.FgDefault, textContentColor = Primer.FgDefault,
        title = { Text("Linha vermelha do motor") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (step) {
                    RedlineStep.CHOOSE -> {
                        Text("O OBD2 não informa o limite de giro do motor. Defina a linha vermelha para o " +
                            "conta-giros marcar a faixa certa.", fontSize = 14.sp, lineHeight = 20.sp)
                        saved?.let {
                            Text("Valor salvo: ${rpmText(it.rpm)} (${it.source.label})",
                                color = Primer.FgMuted, fontSize = 13.sp)
                        }
                        PrimerButton("Medir acelerando até o corte", {
                            peak = RedlinePeak(); step = RedlineStep.MEASURE
                        }, Modifier.fillMaxWidth(), ButtonVariant.PRIMARY)
                        PrimerButton("Digitar o valor", { step = RedlineStep.TYPE }, Modifier.fillMaxWidth())
                        saved?.let {
                            PrimerButton("Manter ${rpmText(it.rpm)}", onDismiss, Modifier.fillMaxWidth())
                        }
                    }
                    RedlineStep.MEASURE -> Measure(rpm, live, peak,
                        onUse = { onSave(RedlineSetting(it, RedlineSource.MEASURED)) },
                        onRestart = { peak = RedlinePeak() }, onBack = { step = RedlineStep.CHOOSE })
                    RedlineStep.TYPE -> {
                        val value = Redline.parse(typed)
                        OutlinedTextField(typed, { typed = it.filter(Char::isDigit).take(5) },
                            label = { Text("Linha vermelha (rpm)") }, singleLine = true,
                            isError = typed.isNotEmpty() && value == null,
                            supportingText = { Text("Entre ${rpmText(Redline.VALID_RANGE.first)} e " +
                                rpmText(Redline.VALID_RANGE.last) + ", como no manual ou no painel do carro.") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth())
                        PrimerButton(value?.let { "Salvar ${rpmText(it)}" } ?: "Salvar", {
                            value?.let { onSave(RedlineSetting(it, RedlineSource.TYPED)) }
                        }, Modifier.fillMaxWidth(), ButtonVariant.PRIMARY, enabled = value != null)
                        PrimerButton("Voltar", { step = RedlineStep.CHOOSE }, Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Agora não") } }
    )
}

@Composable private fun Measure(rpm: Reading, live: Boolean, peak: RedlinePeak,
    onUse: (Int) -> Unit, onRestart: () -> Unit, onBack: () -> Unit) {
    Banner("Carro parado, freio de mão puxado, câmbio em P ou N e motor quente. " +
        "Acelere até o corte de giro e solte.", Tone.ATTENTION, maxLines = 4)

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Agora", color = Primer.FgMuted, fontSize = 12.sp)
            Text(if (live) rpm.value?.let { rpmText(it.roundToInt()) } ?: "--" else "--",
                fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
        }
        Column(Modifier.weight(1f)) {
            Text("Máxima", color = Primer.FgMuted, fontSize = 12.sp)
            Text(if (peak.samples > 0) rpmText(peak.peak.roundToInt()) else "--",
                fontSize = 26.sp, fontWeight = FontWeight.SemiBold, color = Primer.FgSuccess)
        }
    }
    when {
        !live -> StateLabel("Sem leitura de RPM: conecte o adaptador", Tone.ATTENTION)
        peak.cut -> StateLabel("Corte detectado", Tone.SUCCESS)
        else -> StateLabel("Aguardando o corte de giro", Tone.NEUTRAL)
    }
    Text("Em P ou N muitos carros cortam antes da linha vermelha real, e a 5 leituras por segundo o " +
        "pico pode ficar um pouco abaixo do corte. Se o valor parecer baixo, digite o do manual.",
        color = Primer.FgMuted, fontSize = 12.sp, lineHeight = 17.sp)

    val suggestion = Redline.suggestion(peak.peak)
    PrimerButton(suggestion?.let { "Usar ${rpmText(it)}" } ?: "Usar o valor medido",
        { suggestion?.let(onUse) }, Modifier.fillMaxWidth(), ButtonVariant.PRIMARY, enabled = suggestion != null)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PrimerButton("Medir de novo", onRestart, Modifier.weight(1f))
        PrimerButton("Voltar", onBack, Modifier.weight(1f))
    }
}
