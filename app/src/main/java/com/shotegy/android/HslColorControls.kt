// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Hiroshi33XX and contributors.
package com.shotegy.android

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.*

internal data class HslColor(val hue: Float, val saturation: Float, val lightness: Float)
internal fun hslFromHex(hex: String): HslColor? {
    val value = hex.trim().removePrefix("#")
    if (!value.matches(Regex("[0-9a-fA-F]{6}"))) return null
    val n = value.toInt(16); val r = ((n shr 16) and 255) / 255f; val g = ((n shr 8) and 255) / 255f; val b = (n and 255) / 255f
    val max = maxOf(r, g, b); val min = minOf(r, g, b); val d = max - min; val light = (max + min) / 2
    if (d == 0f) return HslColor(0f, 0f, light)
    val sat = d / (1 - abs(2 * light - 1))
    val hue = when (max) { r -> 60f * (((g - b) / d) % 6f); g -> 60f * ((b - r) / d + 2f); else -> 60f * ((r - g) / d + 4f) }
    return HslColor((hue + 360f) % 360f, sat, light)
}
internal fun hslToHex(value: HslColor): String {
    val hue = ((value.hue % 360f) + 360f) % 360f; val s = value.saturation.coerceIn(0f, 1f); val l = value.lightness.coerceIn(0f, 1f)
    val c = (1f - abs(2f * l - 1f)) * s; val x = c * (1f - abs((hue / 60f) % 2f - 1f)); val m = l - c / 2f
    val (r, g, b) = when { hue < 60 -> Triple(c, x, 0f); hue < 120 -> Triple(x, c, 0f); hue < 180 -> Triple(0f, c, x); hue < 240 -> Triple(0f, x, c); hue < 300 -> Triple(x, 0f, c); else -> Triple(c, 0f, x) }
    return String.format(Locale.ROOT, "#%02X%02X%02X", ((r + m) * 255).roundToInt().coerceIn(0, 255), ((g + m) * 255).roundToInt().coerceIn(0, 255), ((b + m) * 255).roundToInt().coerceIn(0, 255))
}

@Composable
internal fun HslColorControls(hex: String, onChange: (String) -> Unit) {
    val wheelDescription = tr("色轮，也可使用下方 HSL 滑块")
    val color = hslFromHex(hex) ?: HslColor(0f, 0f, .5f)
    val latestColor by rememberUpdatedState(color)
    val latestChange by rememberUpdatedState(onChange)
    fun pointer(position: Offset, width: Float, height: Float) {
        val radius = min(width, height) / 2f
        val dx = position.x - width / 2f; val dy = position.y - height / 2f
        val h = ((atan2(dy, dx) * 180f / PI.toFloat()) + 360f) % 360f
        latestChange(hslToHex(latestColor.copy(hue = h, saturation = (hypot(dx, dy) / radius).coerceIn(0f, 1f))))
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Canvas(Modifier.size(200.dp).align(Alignment.CenterHorizontally).semantics { contentDescription = wheelDescription }
            .pointerInput(Unit) { detectTapGestures { pointer(it, size.width.toFloat(), size.height.toFloat()) } }
            .pointerInput(Unit) { detectDragGestures { change, _ -> pointer(change.position, size.width.toFloat(), size.height.toFloat()); change.consume() } }) {
            val radius = size.minDimension / 2f
            drawCircle(Brush.sweepGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)))
            drawCircle(Brush.radialGradient(listOf(Color.Gray, Color.Transparent), center = center, radius = radius))
            drawCircle(if (color.lightness < .5f) Color.Black.copy(alpha = abs(2f * color.lightness - 1f)) else Color.White.copy(alpha = abs(2f * color.lightness - 1f)))
            val angle = color.hue * PI.toFloat() / 180f
            val pos = Offset(center.x + cos(angle) * radius * color.saturation, center.y + sin(angle) * radius * color.saturation)
            drawCircle(Color(android.graphics.Color.parseColor(hslToHex(color))), radius = 10.dp.toPx(), center = pos)
            drawCircle(Color.White, radius = 10.dp.toPx(), center = pos, style = Stroke(width = 3.dp.toPx()))
        }
        OutlinedTextField(hex, onChange, label = { Text("Hex") }, singleLine = true, isError = hslFromHex(hex) == null, modifier = Modifier.fillMaxWidth())
        HslSlider("色相 H", "${color.hue.roundToInt()}°", color.hue, 0f..360f) { onChange(hslToHex(color.copy(hue = it))) }
        HslSlider("饱和度 S", "${(color.saturation * 100).roundToInt()}%", color.saturation, 0f..1f) { onChange(hslToHex(color.copy(saturation = it))) }
        HslSlider("明度 L", "${(color.lightness * 100).roundToInt()}%", color.lightness, 0f..1f) { onChange(hslToHex(color.copy(lightness = it))) }
    }
}

@Composable
private fun HslSlider(label: String, detail: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    val localizedLabel = tr(label)
    Column { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(localizedLabel, style = MaterialTheme.typography.labelMedium); Text(detail, style = MaterialTheme.typography.labelSmall) }; Slider(value = value.coerceIn(range), onValueChange = onChange, valueRange = range, modifier = Modifier.semantics { contentDescription = localizedLabel }) }
}
