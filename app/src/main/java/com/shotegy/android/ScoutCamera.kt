// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Hiroshi33XX and contributors.
package com.shotegy.android

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

private fun uprightCapture(source: File, destination: File) {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }; BitmapFactory.decodeFile(source.path, bounds)
    require(bounds.outWidth > 0 && bounds.outHeight > 0) { "无法读取拍摄照片" }
    var sample = 1; while (bounds.outWidth / sample > 4096 || bounds.outHeight / sample > 4096) sample *= 2
    val image = BitmapFactory.decodeFile(source.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: error("无法读取拍摄照片")
    var rotated: Bitmap? = null
    try {
        val degrees = when (ExifInterface(source.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) { ExifInterface.ORIENTATION_ROTATE_90 -> 90f; ExifInterface.ORIENTATION_ROTATE_180 -> 180f; ExifInterface.ORIENTATION_ROTATE_270 -> 270f; else -> 0f }
        rotated = if (degrees == 0f) image else Bitmap.createBitmap(image, 0, 0, image.width, image.height, Matrix().apply { postRotate(degrees) }, true)
        destination.outputStream().use { check(rotated!!.compress(Bitmap.CompressFormat.JPEG, 92, it)) { "无法保存拍摄照片" } }
    } finally { if (rotated !== image) rotated?.recycle(); image.recycle() }
}

private fun saveCapturedScout(file: File, metadata: CaptureSnapshot, store: Store, context: android.content.Context, deliver: (Result<Scout>) -> Unit) {
    thread(name = "shotegy-scout-save") {
        val upright = File(context.cacheDir, "scout-upright-${UUID.randomUUID()}.jpg")
        val result = runCatching {
            uprightCapture(file, upright)
            val id = store.saveReferenceImage(Uri.fromFile(upright))
            Scout(imageId = id, latitude = metadata.latitude, longitude = metadata.longitude, heading = metadata.heading, headingAccuracy = metadata.headingAccuracy, capturedAt = metadata.capturedAt, timeZoneIdentifier = metadata.timeZone)
        }
        file.delete(); upright.delete()
        Handler(Looper.getMainLooper()).post { deliver(result) }
    }
}

@Composable
internal fun ScoutCamera(store: Store, onCapture: (Scout) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val telemetry = remember { CaptureTelemetry(context) }
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER; implementationMode = PreviewView.ImplementationMode.COMPATIBLE } }
    val imageCapture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build() }
    val preview = remember { Preview.Builder().build().apply { setSurfaceProvider(previewView.surfaceProvider) } }
    var permissionVersion by remember { mutableIntStateOf(0) }
    var ready by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val alive = remember { AtomicBoolean(true) }
    fun permissionSignature() = listOf(Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION).map { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
    var lastPermissions by remember { mutableStateOf(permissionSignature()) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissionVersion++; error = null }
    fun requestPermissions() { request.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }
    LaunchedEffect(Unit) { requestPermissions() }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) { val current = permissionSignature(); if (current != lastPermissions) { lastPermissions = current; permissionVersion++ } } }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    DisposableEffect(lifecycle, permissionVersion) {
        val observer = LifecycleEventObserver { _, event -> when(event) { Lifecycle.Event.ON_START -> telemetry.start(); Lifecycle.Event.ON_STOP -> telemetry.stop(); else -> Unit } }
        lifecycle.lifecycle.addObserver(observer)
        if (lifecycle.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) telemetry.start()
        onDispose { lifecycle.lifecycle.removeObserver(observer); telemetry.stop() }
    }
    DisposableEffect(permissionVersion, lifecycle) {
        var provider: ProcessCameraProvider? = null
        var disposed = false
        ready = false
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener({
                if (!disposed) try {
                    provider = future.get()
                    check(provider!!.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) { "当前设备没有可用后置相机" }
                    provider!!.bindToLifecycle(lifecycle, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
                    ready = true
                } catch (e: Exception) { error = "无法启动相机：${e.localizedMessage}" }
            }, ContextCompat.getMainExecutor(context))
        } else error = "请允许相机权限以拍摄踩点图。"
        onDispose { disposed = true; provider?.unbind(preview, imageCapture) }
    }
    DisposableEffect(Unit) { onDispose { alive.set(false) } }
    fun takePhoto() {
        val metadata = runCatching { telemetry.snapshot() }.getOrElse { error = it.localizedMessage; return }
        // Freeze coordinates, true-north optical-axis direction and time before
        // submitting the shutter, matching iOS CameraPicker's metadataProvider.
        imageCapture.targetRotation = previewView.display?.rotation ?: android.view.Surface.ROTATION_0
        error = null; busy = true
        val file = File(context.cacheDir, "scout-${UUID.randomUUID()}.jpg")
        try {
            val options = ImageCapture.OutputFileOptions.Builder(file).build()
            imageCapture.takePicture(options, ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageSavedCallback {
                override fun onError(exception: ImageCaptureException) { file.delete(); if (alive.get()) { busy = false; error = "拍摄失败，请重试：${exception.localizedMessage}" } }
                override fun onImageSaved(results: ImageCapture.OutputFileResults) {
                    saveCapturedScout(file, metadata, store, context) { result ->
                        if (!alive.get()) result.getOrNull()?.let { store.removeImportedMedia(listOf(it.imageId)) }
                        else { busy = false; result.onSuccess(onCapture).onFailure { error = "无法保存照片：${it.localizedMessage}" } }
                    }
                }
            })
        } catch (e: Exception) { file.delete(); busy = false; error = "无法拍摄：${e.localizedMessage}" }
    }
    FullScreenDialog(onDismissRequest = { if (!busy) onDismiss() }, edgeToEdge = true, dismissOnBackPress = !busy, darkBackground = true) {
        val cameraView = LocalView.current
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
            BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
            val statusHeight = (maxHeight - 144.dp).coerceIn(32.dp, 150.dp)
            Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                Spacer(Modifier.weight(1f))
                Column(Modifier.fillMaxWidth().heightIn(max = statusHeight).verticalScroll(rememberScrollState()).background(Color.Black.copy(alpha = .75f), RoundedCornerShape(12.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    error?.let { Text(trMessage(it), color = Color(0xFFFFD0BA), style = MaterialTheme.typography.bodyMedium) }
                    Text(trMessage(telemetry.status), color = Color.White, style = MaterialTheme.typography.bodySmall)
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { requestPermissions() }) { Text(tr("重新授权"), color = Color.White) }
                    TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }) { Text(tr("定位设置"), color = Color.White) }
                    TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }) { Text(tr("应用设置"), color = Color.White) }
                    TextButton(onClick = { val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager; clipboard.setPrimaryClip(android.content.ClipData.newPlainText("踩点诊断", telemetry.diagnostics() + "\n" + windowLayoutDiagnostics(cameraView))); android.widget.Toast.makeText(context, context.localized("已复制诊断信息（不含精确坐标）"), android.widget.Toast.LENGTH_SHORT).show() }) { Text(tr("复制诊断"), color = Color.White) }
                }
                Row(Modifier.fillMaxWidth().height(84.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Box(Modifier.weight(1f)) { TextButton(enabled = !busy, onClick = onDismiss) { Text(tr("取消"), color = Color.White) } }
                    Box(Modifier.size(72.dp).border(4.dp, Color.White.copy(alpha = .5f), CircleShape).padding(7.dp).background(if (busy || !ready) Color.Gray else Color.White, CircleShape).clickable(enabled = ready && !busy) { takePhoto() }, contentAlignment = Alignment.Center) { if (busy) CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.Black) }
                    Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(12.dp))
            }
            }
        }
    }
}
