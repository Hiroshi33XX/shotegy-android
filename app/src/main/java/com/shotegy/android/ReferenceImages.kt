// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Hiroshi33XX and contributors.
package com.shotegy.android

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun ReferenceImagesEditor(store: Store, images: List<String>, onChange: (List<String>) -> Unit) {
    var error by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        val imported = mutableListOf<String>()
        try {
            uris.take(20).forEach { imported += store.saveReferenceImage(it) }
            if (imported.isNotEmpty()) onChange(images + imported)
        } catch (e: Exception) {
            store.removeImportedMedia(imported)
            error = e.localizedMessage ?: "无法添加图片"
        }
    }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.height(190.dp)) {
        itemsIndexed(images, key = { index, id -> "$index:$id" }) { index, id ->
            var menu by remember { mutableStateOf(false) }
            Box(Modifier.size(150.dp, 190.dp).clip(RoundedCornerShape(14.dp))) {
                ScriptImage(store, id, Modifier.fillMaxSize())
                Box(Modifier.align(Alignment.TopEnd)) {
                    IconButton(onClick = { menu = true }, modifier = Modifier.padding(4.dp)) {
                        Icon(Icons.Outlined.MoreHoriz, tr("图片操作"), Modifier.size(26.dp).background(Color.Black.copy(alpha = .45f), CircleShape).padding(3.dp), tint = Color.White)
                    }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(text = { Text(tr("设为封面")) }, leadingIcon = { Icon(Icons.Outlined.StarOutline, null) }, onClick = { onChange(images.toMutableList().apply { add(0, removeAt(index)) }); menu = false })
                        DropdownMenuItem(text = { Text(tr("删除"), color = MaterialTheme.colorScheme.error) }, leadingIcon = { Icon(Icons.Outlined.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) }, onClick = { onChange(images.filterIndexed { i, _ -> i != index }); menu = false })
                    }
                }
                if (index == 0) PhotoCaption(tr("封面"), Modifier.align(Alignment.BottomStart).padding(8.dp))
            }
        }
        item { AddImageTile(Icons.Outlined.PhotoLibrary, "导入参考图") { picker.launch("image/*") } }
    }
    error?.let { AlertDialog(onDismissRequest = { error = null }, title = { Text(tr("添加图片失败")) }, text = { Text(trMessage(it)) }, confirmButton = { TextButton(onClick = { error = null }) { Text(tr("好")) } }) }
}

@Composable
internal fun AddImageTile(icon: ImageVector, title: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(Modifier.size(150.dp, 190.dp).clip(shape).background(MaterialTheme.colorScheme.primary.copy(alpha = .06f)).border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .28f), shape).clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically)) {
        Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
            Icon(Icons.Outlined.AddCircle, null, Modifier.align(Alignment.BottomEnd).size(18.dp).background(MaterialTheme.colorScheme.surface, CircleShape), tint = MaterialTheme.colorScheme.primary)
        }
        Text(tr(title), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun PhotoCaption(text: String, modifier: Modifier) {
    Text(text, modifier.background(Color.Black.copy(alpha = .5f), CircleShape).padding(horizontal = 8.dp, vertical = 4.dp), color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
internal fun ScoutImagesEditor(store: Store, scouts: List<Scout>, onEdit: (Scout) -> Unit, onDelete: (Scout) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.height(190.dp)) {
        itemsIndexed(scouts, key = { _, scout -> scout.id }) { _, scout ->
            var menu by remember { mutableStateOf(false) }
            Box(Modifier.size(150.dp, 190.dp).clip(RoundedCornerShape(14.dp)).clickable { onEdit(scout) }) {
                ScriptImage(store, scout.imageId, Modifier.fillMaxSize())
                PhotoCaption(scout.note.ifBlank { tr("机位记录") }, Modifier.align(Alignment.BottomStart).padding(8.dp))
                Box(Modifier.align(Alignment.TopEnd)) {
                    IconButton(onClick = { menu = true }, modifier = Modifier.padding(4.dp)) { Icon(Icons.Outlined.MoreHoriz, tr("机位操作"), Modifier.size(26.dp).background(Color.Black.copy(alpha = .45f), CircleShape).padding(3.dp), tint = Color.White) }
                    DropdownMenu(menu, { menu = false }) { DropdownMenuItem(text = { Text(tr("删除机位"), color = MaterialTheme.colorScheme.error) }, onClick = { onDelete(scout); menu = false }) }
                }
            }
        }
        item { AddImageTile(Icons.Outlined.PhotoCamera, "记录机位") { onEdit(Scout()) } }
    }
}

@Composable
fun ReferenceImagesViewer(store: Store, images: List<String>) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(images, key = { index, id -> "$index:$id" }) { _, id -> ScriptImage(store, id, Modifier.size(180.dp).clip(RoundedCornerShape(14.dp))) }
    }
}
