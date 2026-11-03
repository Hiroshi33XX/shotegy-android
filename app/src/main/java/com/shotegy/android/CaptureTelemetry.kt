// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Hiroshi33XX and contributors.
package com.shotegy.android

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.*
import android.location.*
import android.os.*
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import kotlin.math.*

internal data class CaptureFix(val latitude: Double, val longitude: Double, val altitude: Double, val accuracy: Float, val elapsedMillis: Long)
internal data class CaptureAttitude(val matrix: FloatArray, val accuracyDegrees: Double?, val elapsedMillis: Long, val calibrationStatus: Int = SensorManager.SENSOR_STATUS_UNRELIABLE)
internal data class CaptureSnapshot(val latitude: Double, val longitude: Double, val heading: Float, val headingAccuracy: Double?, val capturedAt: Long, val timeZone: String)

internal fun rearCameraAzimuth(matrix: FloatArray, declination: Double): Double? {
    if (matrix.size != 9 || matrix.any { !it.isFinite() } || !declination.isFinite()) return null
    // SensorManager rotates device coordinates into East/North/Up. The rear
    // optical axis points along device -Z, independent of display rotation.
    val east = -matrix[2].toDouble(); val north = -matrix[5].toDouble()
    if (hypot(east, north) < sin(Math.toRadians(15.0))) return null
    return ((Math.toDegrees(atan2(east, north)) + declination) % 360 + 360) % 360
}

internal fun validatedCapture(fix: CaptureFix?, attitude: CaptureAttitude?, elapsedMillis: Long, wallMillis: Long, declination: Double, zone: String, allowCalibratedUnknownAccuracy: Boolean = false): CaptureSnapshot {
    require(fix != null && elapsedMillis - fix.elapsedMillis in 0..30_000 && fix.latitude.isFinite() && fix.longitude.isFinite() && fix.latitude in -90.0..90.0 && fix.longitude in -180.0..180.0 && (fix.latitude != 0.0 || fix.longitude != 0.0) && fix.accuracy.isFinite() && fix.accuracy >= 0) { "尚未获得有效定位，请稍候再拍。" }
    require(fix.accuracy <= 100) { "当前位置精度不足，请移到开阔处后重试。" }
    require(attitude != null && elapsedMillis - attitude.elapsedMillis in 0..5_000) { "尚未获得有效朝向，请移动手机完成指南针校准后重试。" }
    val accuracy = attitude.accuracyDegrees
    val calibratedUnknown = allowCalibratedUnknownAccuracy && accuracy == null && attitude.calibrationStatus >= SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM
    require(calibratedUnknown || accuracy != null && accuracy.isFinite() && accuracy >= 0) { "设备尚未提供可靠朝向，请校准指南针后重试。" }
    require(accuracy == null || accuracy <= 20) { "当前朝向误差约 %.0f°，请远离磁性物体并校准指南针后重试。".format(accuracy) }
    val heading = rearCameraAzimuth(attitude.matrix, declination) ?: error("无法确定摄像头朝向，请将后置摄像头大致水平对准拍摄主体后重试。")
    java.time.ZoneId.of(zone)
    return CaptureSnapshot(fix.latitude, fix.longitude, (heading.toFloat() % 360f), attitude.accuracyDegrees, wallMillis, zone)
}

// Native accelerometer + calibrated magnetic-field fallback for phones whose
// rotation-vector sensor omits heading accuracy or does not emit usable events.
internal fun magneticCameraMatrix(gravity: FloatArray, field: FloatArray): FloatArray? {
    if (gravity.size < 3 || field.size < 3 || (gravity.take(3) + field.take(3)).any { !it.isFinite() }) return null
    val g = sqrt(gravity.take(3).sumOf { (it * it).toDouble() }); val m = sqrt(field.take(3).sumOf { (it * it).toDouble() })
    if (g !in 8.0..12.0 || m !in 10.0..100.0) return null
    val ax = gravity[0] / g; val ay = gravity[1] / g; val az = gravity[2] / g
    var hx = field[1] * az - field[2] * ay; var hy = field[2] * ax - field[0] * az; var hz = field[0] * ay - field[1] * ax
    val h = sqrt(hx * hx + hy * hy + hz * hz); if (h < .1) return null
    hx /= h; hy /= h; hz /= h
    val mx = ay * hz - az * hy; val my = az * hx - ax * hz; val mz = ax * hy - ay * hx
    return floatArrayOf(hx.toFloat(), hy.toFloat(), hz.toFloat(), mx.toFloat(), my.toFloat(), mz.toFloat(), ax.toFloat(), ay.toFloat(), az.toFloat())
}

internal class CaptureTelemetry(private val context: Context) : SensorEventListener, LocationListener {
    private val locations = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val sensors = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private var fix: CaptureFix? = null
    private var fixProvider: String? = null
    private var attitude: CaptureAttitude? = null
    private val samples = mutableMapOf<Int, CaptureAttitude>()
    private val main = Handler(Looper.getMainLooper())
    private val tick = object : Runnable { override fun run() { if (running) { refresh(); main.postDelayed(this, 1000) } } }
    private var providerInfo = ""
    private var sensorInfo = ""
    private var gravitySample: Pair<FloatArray, Long>? = null
    private var magneticSample: Pair<FloatArray, Long>? = null
    private var magneticStatus = SensorManager.SENSOR_STATUS_UNRELIABLE
    private var running = false
    var status by mutableStateOf("正在获取定位与镜头朝向…")
        private set
    @Suppress("MissingPermission")
    fun start() {
        if (running) return
        running = true; fix = null; attitude = null; samples.clear(); gravitySample = null; magneticSample = null; main.post(tick)
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val candidates = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, "fused", LocationManager.PASSIVE_PROVIDER).distinct().filter { it in locations.allProviders && (it != LocationManager.GPS_PROVIDER || fine) }
        val failures = mutableListOf<String>()
        if (fine || coarse) candidates.forEach { provider ->
            runCatching {
                locations.getLastKnownLocation(provider)?.let(::onLocationChanged)
                locations.requestLocationUpdates(provider, 1000, 0f, this, Looper.getMainLooper())
            }.onFailure { failures.add("$provider: ${it.javaClass.simpleName}") }
        }
        providerInfo = if (candidates.isEmpty()) "无可用定位来源" else candidates.joinToString("/") + if (failures.isEmpty()) "" else " (${failures.joinToString()})"
        val registered = listOf(Sensor.TYPE_ROTATION_VECTOR, Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR, Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_MAGNETIC_FIELD).mapNotNull { sensors.getDefaultSensor(it) }.filter { sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        sensorInfo = if (registered.isEmpty()) "设备没有可用的绝对方向传感器" else registered.joinToString(" / ") { it.name }
        refresh()
    }
    fun stop() { running = false; main.removeCallbacks(tick); samples.clear(); runCatching { locations.removeUpdates(this) }; sensors.unregisterListener(this); fix = null; attitude = null }
    override fun onLocationChanged(location: Location) {
        val value = CaptureFix(location.latitude, location.longitude, location.altitude, if (location.hasAccuracy()) location.accuracy else -1f, location.elapsedRealtimeNanos / 1_000_000)
        val previous = fix
        val previousStillUsable = previous != null && previous.accuracy in 0f..100f && SystemClock.elapsedRealtime() - previous.elapsedMillis in 0..30_000
        if ((value.accuracy >= 0 && value.accuracy <= 100 || !previousStillUsable) && (previous == null || value.elapsedMillis >= previous.elapsedMillis)) { fix = value; fixProvider = location.provider }
        refresh()
    }
    override fun onProviderEnabled(provider: String) { refresh() }
    override fun onProviderDisabled(provider: String) { if (fixProvider == provider) fix = null; refresh() }
    @Deprecated("Compatibility callback") override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) { }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) { if (accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE) { sensor?.let { samples.remove(it.type); if (it.type == Sensor.TYPE_MAGNETIC_FIELD) { magneticStatus = accuracy; samples.remove(-1) } }; refresh() } }
    override fun onSensorChanged(event: SensorEvent) {
        val time = event.timestamp / 1_000_000
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> gravitySample = event.values.copyOf() to time
            Sensor.TYPE_MAGNETIC_FIELD -> { magneticSample = event.values.copyOf() to time; magneticStatus = event.accuracy }
            else -> if (event.accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE) samples.remove(event.sensor.type) else {
                val matrix = FloatArray(9); SensorManager.getRotationMatrixFromVector(matrix, event.values)
                val accuracy = event.values.getOrNull(4)?.takeIf { it.isFinite() && it >= 0 }?.let { Math.toDegrees(it.toDouble()) }
                samples[event.sensor.type] = CaptureAttitude(matrix, accuracy, time, event.accuracy)
            }
        }
        val g = gravitySample; val m = magneticSample; val now = SystemClock.elapsedRealtime()
        if (g != null && m != null && now - g.second in 0..500 && now - m.second in 0..500 && magneticStatus >= SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM) {
            val matrix = magneticCameraMatrix(g.first, m.first)
            if (matrix != null) samples[-1] = CaptureAttitude(matrix, null, minOf(g.second, m.second), magneticStatus) else samples.remove(-1)
        } else samples.remove(-1)
        refresh()
    }
    private fun selectAttitude(): CaptureAttitude? {
        val now = SystemClock.elapsedRealtime()
        return samples.values.filter { now - it.elapsedMillis in 0..5_000 }.sortedWith(compareBy<CaptureAttitude> { when { it.accuracyDegrees != null && it.accuracyDegrees.isFinite() && it.accuracyDegrees in 0.0..20.0 -> 0; it.accuracyDegrees == null && it.calibrationStatus >= SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> 1; else -> 2 } }.thenBy { it.accuracyDegrees ?: Double.MAX_VALUE }.thenByDescending { it.elapsedMillis }).firstOrNull()
    }
    fun snapshot(wallMillis: Long = System.currentTimeMillis()): CaptureSnapshot {
        val permitted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        check(running && permitted) { "请允许位置权限，以记录拍摄机位。" }
        attitude = selectAttitude()
        val point = fix
        val declination = point?.let { GeomagneticField(it.latitude.toFloat(), it.longitude.toFloat(), it.altitude.toFloat(), wallMillis).declination.toDouble() } ?: 0.0
        return validatedCapture(point, attitude, SystemClock.elapsedRealtime(), wallMillis, declination, java.time.ZoneId.systemDefault().id, allowCalibratedUnknownAccuracy = true)
    }
    private fun refresh() {
        attitude = selectAttitude()
        val elapsed = SystemClock.elapsedRealtime()
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val locationEnabled = runCatching { locations.isProviderEnabled(LocationManager.GPS_PROVIDER) || locations.isProviderEnabled(LocationManager.NETWORK_PROVIDER) || ("fused" in locations.allProviders && locations.isProviderEnabled("fused")) }.getOrDefault(false)
        val p = fix; val a = attitude
        val locationText = when { !fine && !coarse -> "未授权，请允许位置权限"; !locationEnabled -> "系统定位已关闭"; p == null -> "正在搜索，请到开阔处稍候"; else -> "精度约 ${p.accuracy.toInt()} 米 · ${(elapsed - p.elapsedMillis).coerceAtLeast(0) / 1000} 秒前 · ${fixProvider.orEmpty()}" }
        val headingText = when { a == null -> "等待传感器数据或指南针校准"; rearCameraAzimuth(a.matrix, 0.0) == null -> "请将后置镜头大致水平对准主体"; a.accuracyDegrees == null -> if (a.calibrationStatus >= SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM) "已获取，设备未报告角度误差" else "精度未知，请校准指南针"; else -> "误差约 ±%.0f°".format(a.accuracyDegrees) }
        status = "定位：$locationText${if (coarse && !fine) "（仅近似位置）" else ""}\n朝向：$headingText"
    }
    fun diagnostics(): String = "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n$status\n定位来源：$providerInfo\n传感器：$sensorInfo\n${runCatching { snapshot(); "快门信息校验通过" }.getOrElse { it.localizedMessage ?: "校验未通过" }}"
}
