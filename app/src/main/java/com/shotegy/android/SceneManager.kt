// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Hiroshi33XX and contributors.
package com.shotegy.android

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SceneManager(project: Project, store: Store, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var renaming by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<String?>(null) }
    val scenes = (project.scenes + project.cards.map { it.scene }.filter { it.isNotBlank() }).distinct()
    val valid = name.trim().isNotEmpty() && scenes.none { it == name.trim() && it != renaming }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(tr("场景管理")) }, text = {
        Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(tr("添加场景后，可在拍摄脚本中选择；移动场景会改变分组顺序。"), style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth()) {
                OutlinedTextField(name, { name = it }, label = { Text(if (renaming == null) tr("新场景") else tr("重命名场景")) }, modifier = Modifier.weight(1f), singleLine = true)
                TextButton(enabled = valid, onClick = {
                    val updatedName = name.trim()
                    val previous = renaming
                    val updatedScenes = if (previous == null) scenes + updatedName else scenes.map { if (it == previous) updatedName else it }
                    val updatedCards = if (previous == null) project.cards else project.cards.map { if (it.scene == previous) it.copy(scene = updatedName) else it }
                    store.saveProject(project.copy(scenes = updatedScenes, cards = updatedCards)); name = ""; renaming = null
                }) { Text(if (renaming == null) tr("添加") else tr("保存")) }
            }
            if (renaming != null) TextButton(onClick = { renaming = null; name = "" }) { Text(tr("取消重命名")) }
            scenes.forEachIndexed { index, scene ->
                HorizontalDivider()
                Text(scene, style = MaterialTheme.typography.titleSmall)
                Row {
                    TextButton(enabled = index > 0, onClick = { val order = scenes.toMutableList(); java.util.Collections.swap(order, index, index - 1); store.saveProject(project.copy(scenes = order)) }) { Text(tr("上移")) }
                    TextButton(enabled = index < scenes.lastIndex, onClick = { val order = scenes.toMutableList(); java.util.Collections.swap(order, index, index + 1); store.saveProject(project.copy(scenes = order)) }) { Text(tr("下移")) }
                    TextButton(onClick = { renaming = scene; name = scene }) { Text(tr("重命名")) }
                    TextButton(onClick = { deleting = scene }) { Text(tr("删除")) }
                }
            }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text(tr("完成")) } })
    deleting?.let { scene ->
        AlertDialog(onDismissRequest = { deleting = null }, title = { Text(tr("删除场景？")) }, text = { Text(tr("该场景的拍摄脚本将移至未分组，脚本内容会保留。")) }, confirmButton = {
            TextButton(onClick = { store.saveProject(project.copy(scenes = scenes.filterNot { it == scene }, cards = project.cards.map { if (it.scene == scene) it.copy(scene = "") else it })); deleting = null; if (renaming == scene) { renaming = null; name = "" } }) { Text(tr("删除")) }
        }, dismissButton = { TextButton(onClick = { deleting = null }) { Text(tr("取消")) } })
    }
}
