// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Hiroshi33XX and contributors.
package com.shotegy.android

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.*

internal data class SolarPosition(val azimuth: Double, val elevation: Double)
private fun rad(x: Double) = Math.toRadians(x)
private fun deg(x: Double) = Math.toDegrees(x)
private fun norm(x: Double) = ((x % 360) + 360) % 360

// The same NOAA-based solar-position calculation and two-minute sampling used by iOS.
internal fun sunPosition(time: java.time.ZonedDateTime, latitude: Double, longitude: Double): SolarPosition {
    val jd = time.toEpochSecond() / 86400.0 + 2440587.5
    val t = (jd - 2451545.0) / 36525.0
    val l = norm(280.46646 + t * (36000.76983 + t * 0.0003032))
    val m = 357.52911 + t * (35999.05029 - 0.0001537 * t)
    val e = 0.016708634 - t * (0.000042037 + 0.0000001267 * t)
    val center = sin(rad(m)) * (1.914602 - t * (0.004817 + 0.000014 * t)) + sin(rad(2 * m)) * (0.019993 - 0.000101 * t) + sin(rad(3 * m)) * 0.000289
    val apparent = l + center - 0.00569 - 0.00478 * sin(rad(125.04 - 1934.136 * t))
    val obliquity = 23 + (26 + (21.448 - t * (46.815 + t * (0.00059 - t * 0.001813))) / 60) / 60 + 0.00256 * cos(rad(125.04 - 1934.136 * t))
    val declination = deg(asin(sin(rad(obliquity)) * sin(rad(apparent))))
    val y = tan(rad(obliquity / 2)).pow(2)
    val equation = 4 * deg(y * sin(2 * rad(l)) - 2 * e * sin(rad(m)) + 4 * e * y * sin(rad(m)) * cos(2 * rad(l)) - 0.5 * y * y * sin(4 * rad(l)) - 1.25 * e * e * sin(2 * rad(m)))
    val minutes = time.hour * 60.0 + time.minute + time.second / 60.0
    val solar = ((minutes + equation + 4 * longitude - time.offset.totalSeconds / 60.0) % 1440 + 1440) % 1440
    val hour = rad(solar / 4 - 180)
    val lat = rad(latitude); val dec = rad(declination)
    val elevation = 90 - deg(acos((sin(lat) * sin(dec) + cos(lat) * cos(dec) * cos(hour)).coerceIn(-1.0, 1.0)))
    val azimuth = norm(deg(atan2(sin(hour), cos(hour) * sin(lat) - tan(dec) * cos(lat))) + 180)
    return SolarPosition(azimuth, elevation)
}

internal data class LightWindow(val start: java.time.ZonedDateTime, val end: java.time.ZonedDateTime, val position: SolarPosition)

internal fun lightWindows(date: LocalDate, lat: Double, lon: Double, heading: Double, type: String, tolerance: Double, zone: ZoneId): List<LightWindow> {
    val target = norm(heading + when (type) { "顺光" -> 180.0; "左侧光" -> -90.0; "右侧光" -> 90.0; else -> 0.0 })
    val start = date.atStartOfDay(zone)
    val end = date.plusDays(1).atStartOfDay(zone)
    val groups = mutableListOf<MutableList<java.time.ZonedDateTime>>()
    var time = start
    while (time.toInstant() < end.toInstant()) {
        val sun = sunPosition(time, lat, lon)
        val diff = abs((sun.azimuth - target + 540) % 360 - 180)
        if (sun.elevation >= 2 && diff <= tolerance) {
            if (groups.isEmpty() || java.time.Duration.between(groups.last().last(), time).toMinutes() > 4) groups.add(mutableListOf())
            groups.last().add(time)
        }
        time = time.plusMinutes(2)
    }
    return groups.filter { it.size >= 3 }.sortedByDescending { it.size }.map { group ->
        val first = group.first(); val last = group.last().plusMinutes(2)
        val midpoint = first.plusSeconds(java.time.Duration.between(first, last).seconds / 2)
        LightWindow(first, last, sunPosition(midpoint, lat, lon))
    }
}

@Composable
fun LightPlanner(project: Project, onDismiss: () -> Unit, scout: Scout, onApplyPlan: (LightPlan) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val saved = scout.lightPlans?.firstOrNull()
    val zone = remember(scout.id) { scout.timeZoneIdentifier?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneId.systemDefault() }
    val initialDate = saved?.planDate?.let { value -> runCatching { LocalDate.parse(value) }.getOrElse { runCatching { java.time.Instant.parse(value).atZone(zone).toLocalDate() }.getOrNull() } } ?: runCatching { LocalDate.parse(project.date) }.getOrDefault(LocalDate.now(zone))
    var date by remember(scout.id) { mutableStateOf(initialDate) }
    val lat = scout.latitude?.toString().orEmpty()
    val lon = scout.longitude?.toString().orEmpty()
    val heading = scout.heading?.toString().orEmpty()
    var tolerance by remember(scout.id) { mutableIntStateOf(saved?.tolerance?.takeIf { it in listOf(10, 20, 30) } ?: 20) }
    var type by remember(scout.id) { mutableStateOf(saved?.lightType ?: "逆光") }
    var result by remember { mutableStateOf<List<LightWindow>?>(null) }
    val valid = scoutCanCalculate(scout)
    val formatter = DateTimeFormatter.ofPattern("HH:mm")
    FullScreenDialog(onDismissRequest = onDismiss, edgeToEdge = true) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                NavigationHeader(tr("太阳方向与时间窗"), leading = { TextButton(onClick = onDismiss) { Text(tr("关闭")) } })
                Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    FormSection("") {
                        FormAction("拍摄日期", date.toString()) { android.app.DatePickerDialog(context, { _, year, month, day -> date = LocalDate.of(year, month + 1, day); result = null }, date.year, date.monthValue - 1, date.dayOfMonth).show() }
                        FormDivider()
                        FormPicker("希望获得的光线", type, listOf("顺光", "逆光", "左侧光", "右侧光")) { type = it; result = null }
                        FormDivider()
                        Text(tr("方向容差"), Modifier.padding(start = 16.dp, top = 12.dp), style = MaterialTheme.typography.bodyMedium)
                        Box(Modifier.padding(12.dp)) { FormSegments(listOf("±10°", "±20°", "±30°"), "±${tolerance}°") { tolerance = it.filter(Char::isDigit).toInt(); result = null } }
                        Button(enabled = valid, onClick = { result = lightWindows(date, lat.toDouble(), lon.toDouble(), heading.toDouble(), type, tolerance.toDouble(), zone) }, modifier = Modifier.padding(16.dp)) { Text(tr("☀  计算推荐时间")) }
                    }
                    if (scout.headingAccuracy == null) Text(tr("设备未提供方向误差度数，推荐时间需现场核对。"), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    if (!valid) Text(tr("没有可靠的拍摄坐标和朝向，请重新拍摄踩点图。"), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    result?.let { windows ->
                        if (windows.isEmpty()) Column(Modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) { Text(tr("当天没有匹配时间"), style = MaterialTheme.typography.titleMedium); Text(tr("可放宽方向容差，或选择其他光线类型。"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        else FormSection("推荐时间窗") {
                            windows.take(4).forEachIndexed { index, window ->
                                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("${window.start.format(formatter)}–${window.end.format(formatter)}", style = MaterialTheme.typography.titleMedium)
                                    val target = norm(heading.toDouble() + when(type) { "顺光" -> 180.0; "左侧光" -> -90.0; "右侧光" -> 90.0; else -> 0.0 })
                                    val offset = (window.position.azimuth - target + 540) % 360 - 180
                                    Text(tr("太阳方位 {0}° · 高度 {1}° · 方向偏差 {2}°", window.position.azimuth.roundToInt(), window.position.elevation.roundToInt(), offset.roundToInt()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Button(onClick = { onApplyPlan(LightPlan(id = saved?.id ?: java.util.UUID.randomUUID().toString(), lightType = type, planDate = date.toString(), tolerance = tolerance, recommendedStart = window.start.toInstant().toEpochMilli(), recommendedEnd = window.end.toInstant().toEpochMilli(), sunAzimuth = window.position.azimuth, sunElevation = window.position.elevation)); onDismiss() }) { Text(tr("采用此时间")) }
                                }
                                if (index < minOf(windows.lastIndex, 3)) FormDivider()
                            }
                        }
                    }
                    Text(tr("时间使用踩点时区 {0}", zone.id), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}
