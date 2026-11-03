// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Hiroshi33XX and contributors.
package com.shotegy.android

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicReference

internal fun scoutCanPlan(scout: Scout): Boolean = scout.latitude?.let { it.isFinite() && it in -90.0..90.0 } == true && scout.longitude?.let { it.isFinite() && it in -180.0..180.0 } == true && (scout.latitude != 0.0 || scout.longitude != 0.0) && scout.heading?.let { it.isFinite() && it >= 0 && it < 360 } == true && scout.headingAccuracy?.let { it.isFinite() && it in 0.0..20.0 } == true && scout.timeZoneIdentifier?.let { runCatching { ZoneId.of(it) }.isSuccess } == true

internal fun scoutHasPose(scout: Scout): Boolean = scout.latitude?.let { it.isFinite() && it in -90.0..90.0 } == true && scout.longitude?.let { it.isFinite() && it in -180.0..180.0 } == true && (scout.latitude != 0.0 || scout.longitude != 0.0) && scout.heading?.let { it.isFinite() && it >= 0 && it < 360 } == true && scout.timeZoneIdentifier?.let { runCatching { ZoneId.of(it) }.isSuccess } == true
internal fun scoutCanCalculate(scout: Scout): Boolean = scoutHasPose(scout) && (scout.headingAccuracy == null || scout.headingAccuracy.isFinite() && scout.headingAccuracy in 0.0..20.0)

@Composable
fun ScoutManager(project: Project, store: Store, scouts: List<Scout>, onChange: (List<Scout>) -> Unit, onDismiss: () -> Unit, onPlanApplied: (LightPlan, String?) -> Unit = { _, _ -> }) {
    var editing by remember { mutableStateOf<Scout?>(if (scouts.isEmpty()) Scout() else null) }
    FullScreenDialog(onDismissRequest = onDismiss, edgeToEdge = true) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                NavigationHeader(tr("踩点机位"), leading = { TextButton(onClick = onDismiss) { Text(tr("‹ 返回")) } }) { TextButton(onClick = { editing = Scout() }) { Text("＋") } }
                Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (scouts.isEmpty()) Text(tr("拍摄踩点图，自动记录现场坐标和镜头朝向。"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    scouts.forEach { scout ->
                        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
                            Column(Modifier.fillMaxWidth().clickable { editing = scout }.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                ScriptImage(store, scout.imageId, Modifier.fillMaxWidth().height(210.dp), crop = true)
                                Text(scout.note.ifBlank { tr("踩点机位") }, style = MaterialTheme.typography.titleMedium)
                                Text(if (scoutCanPlan(scout)) tr("真北朝向 %.0f° · 可计算太阳方向").format(scout.heading) else tr("未记录可靠的现场坐标与朝向"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                TextButton(onClick = { onChange(scouts.filterNot { it.id == scout.id }) }) { Text(tr("移除机位"), color = MaterialTheme.colorScheme.error) }
                            }
                        }
                    }
                }
            }
        }
    }
    editing?.let { record -> ScoutRecordEditor(project, store, record, onSave = { saved -> onChange(if (scouts.any { it.id == saved.id }) scouts.map { if (it.id == saved.id) saved else it } else scouts + saved); editing = null }, onDismiss = { editing = null }, onPlanApplied = onPlanApplied) }
}

@Composable
internal fun ScoutRecordEditor(project: Project, store: Store, original: Scout, onSave: (Scout) -> Unit, onDismiss: () -> Unit, onPlanApplied: (LightPlan, String?) -> Unit) {
    val context = LocalContext.current
    var draft by remember { mutableStateOf(original) }
    var camera by remember { mutableStateOf(false) }
    var planner by remember { mutableStateOf(false) }
    var confirmUncertain by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val created = remember { mutableSetOf<String>() }
    val kept = remember { AtomicReference<String?>(null) }
    DisposableEffect(Unit) { onDispose { store.removeImportedMedia(created.filter { it != kept.get() }) } }
    fun replacePhoto(value: Scout) { created.add(value.imageId); draft = value.copy(id = original.id, note = draft.note, lightPlans = emptyList()) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) try { replacePhoto(Scout(imageId = store.saveReferenceImage(uri))) } catch (e: Exception) { error = e.localizedMessage }
    }
    FullScreenDialog(onDismissRequest = onDismiss, edgeToEdge = true) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
                NavigationHeader(if (original.imageId.isBlank()) tr("记录踩点机位") else tr("编辑踩点机位"), leading = { TextButton(onClick = onDismiss) { Text(tr("‹ 返回")) } })
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
                        Column {
                            Box(Modifier.fillMaxWidth().height(240.dp).clickable { camera = true }, contentAlignment = Alignment.Center) {
                                if (draft.imageId.isNotBlank()) ScriptImage(store, draft.imageId, Modifier.fillMaxSize(), crop = true)
                                else Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) { Text("◎", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary); Text(tr("拍摄踩点图"), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium); Text(tr("将后置摄像头对准拍摄主体，并保持大致水平"), style = MaterialTheme.typography.bodySmall) }
                                if (draft.imageId.isNotBlank()) FilledTonalButton(onClick = { camera = true }, modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)) { Text(tr("重拍")) }
                            }
                            TextButton(onClick = { gallery.launch("image/*") }, modifier = Modifier.fillMaxWidth()) { Text(tr("从相册选择")) }
                            FormDivider(); FormText(draft.note, { draft = draft.copy(note = it) }, "机位备注（可选）", true)
                        }
                    }
                    if (draft.imageId.isNotBlank()) {
                        val zone = draft.timeZoneIdentifier?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneId.systemDefault()
                        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
                            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(tr("拍摄信息"), style = MaterialTheme.typography.titleSmall)
                                Text(Instant.ofEpochMilli(draft.capturedAt).atZone(zone).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")), style = MaterialTheme.typography.bodySmall)
                                if (scoutHasPose(draft)) { Text(tr("坐标 %.6f, %.6f").format(draft.latitude, draft.longitude)); Text(tr("镜头朝向 %.1f°（真北）").format(draft.heading)); Text(draft.headingAccuracy?.let { tr("方向误差约 ±%.0f°").format(it) } ?: tr("设备未报告方向误差度数，规划结果需现场核对")); Text(tr("时区：{0}", zone.id), style = MaterialTheme.typography.bodySmall) }
                                else Text(tr("这张图片没有可靠的现场坐标与镜头朝向。请直接拍摄踩点图后再计算太阳方向。"), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    Surface(onClick = { if (scoutCanPlan(draft)) planner = true else if (scoutCanCalculate(draft) && draft.headingAccuracy == null) confirmUncertain = true else error = "请直接拍摄踩点图，并允许定位与指南针访问后再使用太阳方向计算。相册图片不能代表当前镜头朝向。" }, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) { Text(tr("☀  计算太阳方向与建议时间  ›"), Modifier.fillMaxWidth().padding(18.dp), style = MaterialTheme.typography.titleMedium) }
                }
                Button(enabled = draft.imageId.isNotBlank(), onClick = { kept.set(draft.imageId); onSave(draft) }, modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 10.dp), contentPadding = PaddingValues(horizontal = 26.dp, vertical = 12.dp)) { Text(if (original.imageId.isBlank()) tr("保存机位") else tr("保存修改")) }
            }
        }
    }
    if (confirmUncertain) AlertDialog(onDismissRequest = { confirmUncertain = false }, title = { Text(tr("方向精度未知")) }, text = { Text(tr("已记录拍摄时的坐标与真北朝向，但设备未提供误差度数。请先校准指南针，并将推荐时间作为现场参考。")) }, confirmButton = { TextButton(onClick = { confirmUncertain = false; planner = true }) { Text(tr("继续计算")) } }, dismissButton = { TextButton(onClick = { confirmUncertain = false }) { Text(tr("取消")) } })
    if (camera) ScoutCamera(store, onCapture = { replacePhoto(it); camera = false }, onDismiss = { camera = false })
    if (planner) LightPlanner(project, onDismiss = { planner = false }, scout = draft, onApplyPlan = { plan -> draft = draft.copy(lightPlans = listOf(plan)); onPlanApplied(plan, draft.timeZoneIdentifier) })
    error?.let { AlertDialog(onDismissRequest = { error = null }, title = { Text(tr("无法完成操作")) }, text = { Text(trMessage(it)) }, confirmButton = { TextButton(onClick = { error = null }) { Text(tr("好")) } }) }
}
