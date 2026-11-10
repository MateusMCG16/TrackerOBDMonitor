package br.com.tracker.obd.ui

import androidx.compose.foundation.Canvas
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
 * The four destinations. Each one is sized to a single screen: no destination
 * scrolls, so the enum is also the contract that the content must fit.
 */
enum class Tab(val label: String) {
    INSTRUMENTS("Instrumentos"),
    DATA("Dados"),
    DIAGNOSTICS("Diagnóstico"),
    HELP("Ajuda")
}

@Composable fun TrackerNavBar(current: Tab, alert: Boolean, notice: Boolean, onSelect: (Tab) -> Unit) {
    Column {
    HorizontalDivider(color = Primer.BorderDefault, thickness = 1.dp)
    NavigationBar(containerColor = Primer.BgInset, contentColor = Primer.FgMuted, tonalElevation = 0.dp) {
        Tab.entries.forEach { tab ->
            val selected = tab == current
            NavigationBarItem(
                selected = selected,
                onClick = { onSelect(tab) },
                icon = {
                    Box {
                        TabGlyph(tab, if (selected) Primer.FgDefault else Primer.FgMuted)
                        val dot = when (tab) {
                            Tab.DIAGNOSTICS -> if (alert) Primer.FgDanger else null
                            Tab.HELP -> if (notice) Primer.FgAttention else null
                            else -> null
                        }
                        if (dot != null) Box(
                            Modifier.align(Alignment.TopEnd).offset(x = 5.dp, y = (-3).dp)
                                .size(7.dp).clip(CircleShape).background(dot)
                        )
                    }
                },
                label = { Text(tab.label, fontSize = 12.sp,
                    fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.SemiBold else null) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Primer.FgDefault, selectedTextColor = Primer.FgDefault,
                    unselectedIconColor = Primer.FgMuted, unselectedTextColor = Primer.FgMuted,
                    // Como o UnderlineNav do Primer: o item ativo ganha só um realce neutro.
                    indicatorColor = Primer.BgNeutralMuted
                )
            )
        }
    }
    }
}

/** Glyphs are drawn: the project ships no icon dependency, by design. */
@Composable private fun TabGlyph(tab: Tab, color: Color) {
    Canvas(Modifier.size(22.dp)) {
        when (tab) {
            Tab.INSTRUMENTS -> dialGlyph(color)
            Tab.DATA -> barsGlyph(color)
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

private fun DrawScope.barsGlyph(color: Color) {
    val w = size.width
    val h = size.height
    for ((x, top) in listOf(0.24f to 0.52f, 0.50f to 0.22f, 0.76f to 0.38f))
        drawLine(color, androidx.compose.ui.geometry.Offset(w * x, h * 0.82f),
            androidx.compose.ui.geometry.Offset(w * x, h * top), w * 0.13f, cap = StrokeCap.Round)
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

/**
 * Topics inside one destination, as the Primer SegmentedControl: one track in
 * bgColor-muted, the active segment raised in bgColor-default with a border.
 */
@Composable fun ChipRow(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().background(Primer.BgMuted, Primer.RadiusMedium)
        .border(1.dp, Primer.BorderDefault, Primer.RadiusMedium).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        labels.forEachIndexed { index, label ->
            val active = index == selected
            Surface(onClick = { onSelect(index) }, modifier = Modifier.weight(1f),
                shape = Primer.RadiusMedium,
                color = if (active) Primer.ButtonBg else Color.Transparent,
                border = if (active) androidx.compose.foundation.BorderStroke(1.dp, Primer.BorderDefault) else null) {
                Text(label, color = if (active) Primer.FgDefault else Primer.FgMuted, fontSize = 12.sp, maxLines = 1,
                    fontWeight = if (active) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Normal,
                    softWrap = false, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(vertical = 7.dp, horizontal = 2.dp))
            }
        }
    }
}

/**
 * The Primer PageHeader every destination shows: a small context line, the title,
 * and a muted description, kept to three short lines so no screen spends height on it.
 */
@Composable fun ScreenHeader(eyebrow: String, title: String, subtitle: String, accent: Color = Primer.FgMuted,
    trailing: (@Composable () -> Unit)? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f)) {
            Text(eyebrow, color = accent, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 1)
            Text(title, style = MaterialTheme.typography.titleLarge, color = Primer.FgDefault, maxLines = 1)
            Text(subtitle, color = Primer.FgMuted, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 1)
        }
        trailing?.invoke()
    }
}
