package br.com.tracker.obd.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.tracker.obd.ui.theme.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The three destinations. Each one is sized to a single screen: no destination
 * scrolls, so the enum is also the contract that the content must fit.
 */
enum class Tab(val label: String) {
    INSTRUMENTS("Instrumentos"),
    DIAGNOSTICS("Diagnóstico"),
    HELP("Ajuda")
}

@Composable fun TrackerNavBar(current: Tab, alert: Boolean, notice: Boolean, onSelect: (Tab) -> Unit) {
    NavigationBar(containerColor = Color(0xFF101A28), contentColor = Muted, tonalElevation = 0.dp) {
        Tab.entries.forEach { tab ->
            val selected = tab == current
            NavigationBarItem(
                selected = selected,
                onClick = { onSelect(tab) },
                icon = {
                    Box {
                        TabGlyph(tab, if (selected) Cyan else Muted)
                        val dot = when (tab) {
                            Tab.DIAGNOSTICS -> if (alert) Warning else null
                            Tab.HELP -> if (notice) Warning else null
                            else -> null
                        }
                        if (dot != null) Box(
                            Modifier.align(Alignment.TopEnd).offset(x = 5.dp, y = (-3).dp)
                                .size(7.dp).clip(CircleShape).background(dot)
                        )
                    }
                },
                label = { Text(tab.label, fontSize = 11.sp) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Cyan, selectedTextColor = Cyan,
                    unselectedIconColor = Muted, unselectedTextColor = Muted,
                    indicatorColor = Color(0xFF1B2A3E)
                )
            )
        }
    }
}

/** Glyphs are drawn: the project ships no icon dependency, by design. */
@Composable private fun TabGlyph(tab: Tab, color: Color) {
    Canvas(Modifier.size(22.dp)) {
        when (tab) {
            Tab.INSTRUMENTS -> dialGlyph(color)
            Tab.DIAGNOSTICS -> engineGlyph(color)
            Tab.HELP -> helpGlyph(color)
        }
    }
}

private fun DrawScope.dialGlyph(color: Color) {
    val stroke = Stroke(size.width * 0.1f, cap = StrokeCap.Round)
    drawArc(color, 150f, 240f, false, androidx.compose.ui.geometry.Offset(size.width * 0.1f, size.height * 0.1f),
        androidx.compose.ui.geometry.Size(size.width * 0.8f, size.height * 0.8f), style = stroke)
    val angle = 205.0 * PI / 180
    val center = androidx.compose.ui.geometry.Offset(size.width / 2, size.height / 2)
    drawLine(color, center, center + androidx.compose.ui.geometry.Offset(
        (cos(angle) * size.width * 0.33f).toFloat(), (sin(angle) * size.height * 0.33f).toFloat()),
        size.width * 0.1f, cap = StrokeCap.Round)
}

private fun DrawScope.engineGlyph(color: Color) {
    val w = size.width
    val h = size.height
    val body = Path().apply {
        moveTo(w * 0.20f, h * 0.38f); lineTo(w * 0.38f, h * 0.38f); lineTo(w * 0.42f, h * 0.26f)
        lineTo(w * 0.60f, h * 0.26f); lineTo(w * 0.60f, h * 0.38f); lineTo(w * 0.74f, h * 0.38f)
        lineTo(w * 0.74f, h * 0.46f); lineTo(w * 0.88f, h * 0.46f); lineTo(w * 0.88f, h * 0.62f)
        lineTo(w * 0.74f, h * 0.62f); lineTo(w * 0.74f, h * 0.72f); lineTo(w * 0.28f, h * 0.72f)
        lineTo(w * 0.28f, h * 0.64f); lineTo(w * 0.12f, h * 0.64f); lineTo(w * 0.12f, h * 0.56f)
        lineTo(w * 0.20f, h * 0.56f); lineTo(w * 0.20f, h * 0.48f); lineTo(w * 0.12f, h * 0.48f)
        lineTo(w * 0.12f, h * 0.40f); lineTo(w * 0.20f, h * 0.40f); close()
    }
    drawPath(body, color, style = Stroke(w * 0.085f))
}

private fun DrawScope.helpGlyph(color: Color) {
    val stroke = Stroke(size.width * 0.09f, cap = StrokeCap.Round)
    drawCircle(color, size.width * 0.42f, androidx.compose.ui.geometry.Offset(size.width / 2, size.height / 2),
        style = stroke)
    val hook = Path().apply {
        moveTo(size.width * 0.38f, size.height * 0.38f)
        cubicTo(size.width * 0.40f, size.height * 0.24f, size.width * 0.68f, size.height * 0.28f,
            size.width * 0.54f, size.height * 0.48f)
        lineTo(size.width * 0.50f, size.height * 0.58f)
    }
    drawPath(hook, color, style = stroke)
    drawCircle(color, size.width * 0.055f,
        androidx.compose.ui.geometry.Offset(size.width * 0.50f, size.height * 0.72f))
}

/** The one-line header every destination shows, so no screen spends height on a title block. */
@Composable fun ScreenHeader(eyebrow: String, title: String, subtitle: String, accent: Color = Cyan) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(eyebrow, color = accent, fontSize = 9.sp, letterSpacing = 2.5.sp)
        Text(title, fontSize = 19.sp, style = MaterialTheme.typography.titleLarge)
        Text(subtitle, color = Muted, fontSize = 11.sp, maxLines = 1)
    }
}
