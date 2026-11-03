// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Hiroshi33XX and contributors.
package com.shotegy.android

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

@Composable
internal fun ScriptReorderEditor(project: Project, store: Store, onDismiss: () -> Unit, onSave: ((List<ShotCard>) -> Unit)? = null) {
    var order by remember { mutableStateOf(project.cards) }
    val threshold = with(LocalDensity.current) { 72.dp.toPx() }
    FullScreenDialog(onDismissRequest = onDismiss) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.statusBarsPadding().navigationBarsPadding()) {
                NavigationHeader(tr("调整脚本顺序"), leading = { TextButton(onClick = onDismiss) { Text(tr("取消")) } }) { TextButton(onClick = { if (onSave != null) onSave(order) else { val latest = store.projects.firstOrNull { it.id == project.id } ?: project; store.saveProject(latest.copy(cards = order)) }; onDismiss() }) { Text(tr("完成")) } }
                Text(tr("按住卡片右侧的排序图标并拖到目标位置"), Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    itemsIndexed(order, key = { _, card -> card.id }) { index, card ->
                        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface) {
                            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                ScriptImage(store, card.referenceImages.firstOrNull(), Modifier.size(58.dp))
                                Column(Modifier.weight(1f)) { Text(tr("拍摄脚本 %02d").format(index + 1), style = MaterialTheme.typography.titleSmall); Text(localizedScriptSummary(card), maxLines = 1, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                var menu by remember { mutableStateOf(false) }
                                Box(Modifier.pointerInput(card.id) {
                                    var distance = 0f
                                    detectDragGesturesAfterLongPress(onDragStart = { distance = 0f }, onDrag = { change, delta ->
                                        change.consume(); distance += delta.y
                                        if (kotlin.math.abs(distance) >= threshold) {
                                            val current = order.indexOfFirst { it.id == card.id }; val target = (current + if (distance > 0) 1 else -1).coerceIn(0, order.lastIndex)
                                            if (current != target) order = order.toMutableList().apply { java.util.Collections.swap(this, current, target) }
                                            distance = 0f
                                        }
                                    })
                                }) {
                                    TextButton(onClick = { menu = true }) { Text("☰") }
                                    DropdownMenu(menu, { menu = false }) {
                                        DropdownMenuItem(enabled = index > 0, text = { Text(tr("上移")) }, onClick = { order = order.toMutableList().apply { java.util.Collections.swap(this, index, index - 1) }; menu = false })
                                        DropdownMenuItem(enabled = index < order.lastIndex, text = { Text(tr("下移")) }, onClick = { order = order.toMutableList().apply { java.util.Collections.swap(this, index, index + 1) }; menu = false })
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
