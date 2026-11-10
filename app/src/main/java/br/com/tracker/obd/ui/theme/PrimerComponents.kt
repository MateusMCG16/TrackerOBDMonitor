package br.com.tracker.obd.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Variantes de cor que os componentes do Primer compartilham. */
enum class Tone(val fg: Color, val bg: Color, val border: Color, val emphasis: Color) {
    NEUTRAL(Primer.FgMuted, Primer.BgNeutralMuted, Primer.BorderDefault, Primer.BgNeutralMuted),
    ACCENT(Primer.FgAccent, Primer.BgAccentMuted, Primer.BorderAccentMuted, Primer.BgAccentEmphasis),
    SUCCESS(Primer.FgSuccess, Primer.BgSuccessMuted, Primer.BorderSuccessMuted, Primer.BgSuccessEmphasis),
    ATTENTION(Primer.FgAttention, Primer.BgAttentionMuted, Primer.BorderAttentionMuted, Primer.FgAttention),
    DANGER(Primer.FgDanger, Primer.BgDangerMuted, Primer.BorderDangerMuted, Primer.BgDangerEmphasis)
}

/** Box do Primer: borda de 1 dp, raio 6, cabeçalho opcional em bgColor-muted. */
@Composable fun PrimerBox(
    modifier: Modifier = Modifier, title: String? = null, border: Color = Primer.BorderDefault,
    trailing: (@Composable RowScope.() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier.clip(Primer.RadiusMedium).background(Primer.BgDefault)
        .border(1.dp, border, Primer.RadiusMedium)) {
        if (title != null) {
            Row(Modifier.fillMaxWidth().background(Primer.BgMuted).padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, color = Primer.FgDefault, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                trailing?.invoke(this)
            }
            HorizontalDivider(color = border, thickness = 1.dp)
        }
        content()
    }
}

/** StateLabel: pílula cheia com o estado de algo (conexão, luz de injeção). */
@Composable fun StateLabel(text: String, tone: Tone, modifier: Modifier = Modifier) {
    val (bg, fg) = when (tone) {
        Tone.NEUTRAL -> Primer.BgNeutralMuted to Primer.FgMuted
        Tone.ATTENTION -> Primer.FgAttention to Color(0xFF1F2328)
        else -> tone.emphasis to Primer.FgOnEmphasis
    }
    Text(text, color = fg, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1,
        modifier = modifier.background(bg, Primer.RadiusFull).padding(horizontal = 9.dp, vertical = 3.dp))
}

/** Label: pílula só com borda, para classificar (tipo de código, etc.). */
@Composable fun PrimerLabel(text: String, tone: Tone = Tone.NEUTRAL, modifier: Modifier = Modifier) {
    Text(text, color = tone.fg, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1,
        modifier = modifier.border(1.dp, tone.border, Primer.RadiusFull).padding(horizontal = 7.dp, vertical = 1.dp))
}

/** Counter: número pequeno em pílula neutra. */
@Composable fun Counter(text: String, modifier: Modifier = Modifier) {
    Text(text, color = Primer.FgDefault, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1,
        modifier = modifier.background(Primer.BgNeutralMuted, Primer.RadiusFull).padding(horizontal = 7.dp, vertical = 1.dp))
}

/** Banner: fundo e borda suaves da cor do tom, marcador colorido à esquerda. */
@Composable fun Banner(text: String, tone: Tone, modifier: Modifier = Modifier, maxLines: Int = 2) {
    Row(modifier.fillMaxWidth().background(tone.bg, Primer.RadiusMedium)
        .border(1.dp, tone.border, Primer.RadiusMedium).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(tone.fg))
        Text(text, color = Primer.FgDefault, fontSize = 12.sp, lineHeight = 17.sp, maxLines = maxLines,
            overflow = TextOverflow.Ellipsis)
    }
}

/**
 * Toast do Primer: cartão flutuante em bgColor-muted, com sombra e ícone colorido à
 * esquerda. Mostra o texto inteiro (sem cortar) e fecha com um toque.
 */
@Composable fun PrimerToast(text: String, tone: Tone, modifier: Modifier = Modifier, onDismiss: () -> Unit = {}) {
    Surface(onClick = onDismiss, modifier = modifier.fillMaxWidth(), shape = Primer.RadiusMedium,
        color = Primer.BgMuted, border = BorderStroke(1.dp, Primer.BorderDefault), shadowElevation = 8.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(44.dp).heightIn(min = 52.dp).background(tone.bg), contentAlignment = Alignment.Center) {
                Text("!", color = tone.fg, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Text(text, color = Primer.FgDefault, fontSize = 13.sp, lineHeight = 18.sp,
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 10.dp))
        }
    }
}

enum class ButtonVariant { DEFAULT, PRIMARY, DANGER }

/** Button do Primer, tamanho large (40 dp) para o dedo no carro. */
@Composable fun PrimerButton(
    text: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.DEFAULT, enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null
) {
    val (bg, fg, border) = when (variant) {
        ButtonVariant.PRIMARY -> Triple(Primer.BgSuccessEmphasis, Primer.FgOnEmphasis, Color(0x1AF0F6FC))
        ButtonVariant.DANGER -> Triple(Primer.ButtonBg, Primer.FgDanger, Primer.ButtonBorder)
        ButtonVariant.DEFAULT -> Triple(Primer.ButtonBg, Primer.FgDefault, Primer.ButtonBorder)
    }
    Button(onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = 40.dp),
        shape = Primer.RadiusMedium, border = BorderStroke(1.dp, border),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
        colors = ButtonDefaults.buttonColors(containerColor = bg, contentColor = fg,
            disabledContainerColor = Primer.ButtonBg, disabledContentColor = Primer.FgMuted.copy(alpha = 0.6f))) {
        if (leading != null) { leading(); Spacer(Modifier.width(8.dp)) }
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}
