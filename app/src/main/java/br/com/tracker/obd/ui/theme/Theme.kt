package br.com.tracker.obd.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Graphite = Color(0xFF0B0F17)
val Panel = Color(0xFF131D2D)
val Electric = Color(0xFF438AFF)
val Cyan = Color(0xFF39D9FF)
val Muted = Color(0xFF8FA3BE)
val Warning = Color(0xFFFFC46B)
val Alert = Color(0xFFFF5572)
private val palette = darkColorScheme(primary = Cyan, secondary = Electric, background = Graphite,
    surface = Panel, onSurface = Color(0xFFEAF4FF), onBackground = Color(0xFFEAF4FF), error = Alert)
@Composable fun TrackerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = palette, typography = Typography(
        titleLarge = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Bold, fontSize = 23.sp),
        bodyMedium = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, lineHeight = 21.sp)
    ), content = content)
}
