package br.com.tracker.obd.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Tokens do Primer (sistema de design do GitHub), tema escuro. O app é usado no carro,
 * então fica sempre no escuro; os nomes seguem os do Primer para casar com o painel web.
 */
object Primer {
    val BgDefault = Color(0xFF0D1117)
    val BgMuted = Color(0xFF151B23)
    val BgInset = Color(0xFF010409)
    val BgNeutralMuted = Color(0x33656C76)
    val BgAccentMuted = Color(0x1A388BFD)
    val BgSuccessMuted = Color(0x262EA043)
    val BgAttentionMuted = Color(0x26BB8009)
    val BgDangerMuted = Color(0x1AF85149)
    val BgAccentEmphasis = Color(0xFF1F6FEB)
    val BgSuccessEmphasis = Color(0xFF238636)
    val BgDangerEmphasis = Color(0xFFDA3633)

    val FgDefault = Color(0xFFF0F6FC)
    val FgMuted = Color(0xFF9198A1)
    val FgAccent = Color(0xFF4493F8)
    val FgSuccess = Color(0xFF3FB950)
    val FgAttention = Color(0xFFD29922)
    val FgDanger = Color(0xFFF85149)
    val FgOnEmphasis = Color(0xFFFFFFFF)

    val BorderDefault = Color(0xFF3D444D)
    val BorderMuted = Color(0xB33D444D)
    val BorderAccentMuted = Color(0x66388BFD)
    val BorderSuccessMuted = Color(0x662EA043)
    val BorderAttentionMuted = Color(0x66BB8009)
    val BorderDangerMuted = Color(0x66F85149)

    val ButtonBg = Color(0xFF212830)
    val ButtonBorder = BorderDefault

    val RadiusSmall = RoundedCornerShape(3.dp)
    val RadiusMedium = RoundedCornerShape(6.dp)
    val RadiusLarge = RoundedCornerShape(12.dp)
    val RadiusFull = RoundedCornerShape(50)

    val Mono = FontFamily.Monospace
}

private val palette = darkColorScheme(
    primary = Primer.BgSuccessEmphasis, onPrimary = Primer.FgOnEmphasis,
    secondary = Primer.FgAccent, background = Primer.BgDefault, onBackground = Primer.FgDefault,
    surface = Primer.BgDefault, onSurface = Primer.FgDefault, surfaceVariant = Primer.BgMuted,
    onSurfaceVariant = Primer.FgMuted, outline = Primer.BorderDefault, outlineVariant = Primer.BorderMuted,
    surfaceContainerHigh = Primer.BgMuted, error = Primer.FgDanger
)

@Composable fun TrackerTheme(content: @Composable () -> Unit) {
    // Primer usa a fonte do sistema: no Android, Roboto. Tamanhos da escala do Primer.
    MaterialTheme(colorScheme = palette, shapes = Shapes(
        extraSmall = Primer.RadiusSmall, small = Primer.RadiusMedium, medium = Primer.RadiusMedium,
        large = Primer.RadiusLarge, extraLarge = Primer.RadiusLarge
    ), typography = Typography(
        titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 30.sp),
        titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp),
        bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
        bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp),
        labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp)
    ), content = content)
}
