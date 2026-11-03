// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Hiroshi33XX and contributors.
package com.shotegy.android

import androidx.compose.foundation.clickable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.AddCircle
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.UUID

private val libraryKinds = listOf("项目模板", "引导词库", "方法库")
private fun freshId() = UUID.randomUUID().toString()
internal fun addToFolder(item: LibraryItem, folderId: String): LibraryItem = if (folderId in item.folderIds) item else item.copy(folderIds = item.folderIds + folderId)
internal fun removeFromFolder(item: LibraryItem, folderId: String): LibraryItem = item.copy(folderIds = item.folderIds.filterNot { it == folderId })

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(store: Store) {
    val exportLibrary = rememberBackupExporter(store)
    var kind by rememberSaveable { mutableStateOf(libraryKinds[0]) }
    var query by rememberSaveable { mutableStateOf("") }
    var folderId by rememberSaveable { mutableStateOf<String?>(null) }
    var folderEditor by remember { mutableStateOf<LibraryFolder?>(null) }
    var newFolder by remember { mutableStateOf(false) }
    var deletingFolder by remember { mutableStateOf<LibraryFolder?>(null) }
    var itemEditor by remember { mutableStateOf<LibraryItem?>(null) }
    var newItem by remember { mutableStateOf(false) }
    var deletingItem by remember { mutableStateOf<LibraryItem?>(null) }
    var preview by remember { mutableStateOf<LibraryItem?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var selecting by remember { mutableStateOf(false) }
    var selection by remember { mutableStateOf<Set<String>>(emptySet()) }
    var chooseDestination by remember { mutableStateOf(false) }
    var confirmBatchDelete by remember { mutableStateOf(false) }
    fun finishSelection() { selecting = false; selection = emptySet() }
    BackHandler(enabled = folderId != null) { if (selecting) finishSelection() else folderId = null }
    val folders = store.folders.filter { it.kind == kind }.sortedWith(compareBy<LibraryFolder> { it.sortOrder }.thenBy { it.createdAt })
    val currentFolder = if (folderId == "__default__") LibraryFolder(id = "__default__", kind = kind, name = "默认收藏夹") else folders.firstOrNull { it.id == folderId }
    val resources = store.resources.filter {
        it.kind == kind && (currentFolder == null || currentFolder.id == "__default__" || currentFolder.id in it.folderIds) &&
            (query.isBlank() || it.title.contains(query, true) || it.content.contains(query, true))
    }.sortedByDescending { it.updatedAt }
    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = {
        val headerActions: @Composable RowScope.() -> Unit = {
            if (currentFolder == null) {
                var open by remember { mutableStateOf(false) }
                Box { IconButton(onClick = { open = true }) { Icon(Icons.Outlined.MoreVert, tr("资源库操作")) }
                    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                        DropdownMenuItem(text = { Text(tr("导出资源库")) }, onClick = { open = false; exportLibrary("library") })
                        DropdownMenuItem(text = { Text(if (kind == "项目模板") tr("新建项目模板") else tr("新增{0}", tr(kind))) }, onClick = { open = false; newItem = true })
                    }
                }
                IconButton(onClick = { newFolder = true }) { Icon(Icons.Outlined.Add, tr("新建收藏夹")) }
            }
        }
        if (currentFolder == null) MainPageHeader(tr("资源库"), actions = headerActions)
        else NavigationHeader(if (currentFolder.id == "__default__") tr("默认收藏夹") else currentFolder.name, leading = { TextButton(onClick = { if (selecting) finishSelection() else folderId = null }) { Text(tr("‹ 返回")) } }, actions = headerActions)
    }) { inset ->
        Column(Modifier.fillMaxSize().padding(inset).padding(horizontal = 16.dp)) {
            if (currentFolder == null) {
                OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(tr("搜索资源库")) }, leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    singleLine = true, shape = RoundedCornerShape(17.dp), colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = MaterialTheme.colorScheme.surface, unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent))
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(9.dp)).padding(3.dp)) {
                    libraryKinds.forEach { category ->
                        Box(Modifier.weight(1f).background(if (kind == category) MaterialTheme.colorScheme.surface else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(7.dp)).clickable { kind = category; query = "" }.padding(vertical = 8.dp), contentAlignment = Alignment.Center) { Text(tr(category), style = MaterialTheme.typography.labelLarge) }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            if (currentFolder == null && query.isBlank()) {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 100.dp)) {
                    item { LibraryFolderTile(tr("默认收藏夹"), store.resources.count { it.kind == kind }, onClick = { folderId = "__default__" }) }
                    items(folders, key = { it.id }) { folder -> LibraryFolderTile(folder.name, store.resources.count { folder.id in it.folderIds }, onClick = { folderId = folder.id }) }
                    item {
                        Surface(onClick = { newFolder = true }, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(width = 82.dp, height = 58.dp), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.AddCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp)) }
                                Column { Text(tr("新建收藏夹"), fontWeight = FontWeight.SemiBold); Text(tr("按主题、风格或拍摄类型整理资源"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                        }
                    }
                }
            } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (currentFolder != null) tr("{0} 个内容", resources.size) else tr("搜索结果"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 12.dp))
                Row {
                    if (currentFolder != null && !selecting) {
                        var showMenu by remember(currentFolder.id) { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { showMenu = true }) { Icon(Icons.Outlined.MoreVert, tr("收藏夹操作")) }
                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                if (currentFolder.id != "__default__") DropdownMenuItem(text = { Text(tr("重命名收藏夹")) }, onClick = { folderEditor = currentFolder; showMenu = false })
                                DropdownMenuItem(text = { Text(tr("批量整理内容")) }, onClick = { selecting = true; selection = emptySet(); showMenu = false })
                                if (currentFolder.id != "__default__") DropdownMenuItem(text = { Text(tr("删除收藏夹")) }, onClick = { deletingFolder = currentFolder; showMenu = false })
                            }
                        }
                    }
                    if (selecting) TextButton(onClick = { finishSelection() }) { Text(tr("完成")) }
                    else TextButton(onClick = { if (kind == "项目模板") notice = "请在项目中选择存为项目模板。" else newItem = true }) { Text(tr("＋ 新增")) }
                }
            }
            if (resources.isEmpty()) {
                Text(if (query.isNotBlank()) tr("没有找到匹配的资源") else if (currentFolder != null) tr("收藏夹为空") else tr("暂无{0}", tr(kind)), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 24.dp))
            } else {
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 100.dp)) {
                    items(resources, key = { it.id }) { item ->
                        ElevatedCard(modifier = Modifier.fillMaxWidth().clickable { if (selecting) selection = if (item.id in selection) selection - item.id else selection + item.id else if (item.kind == "项目模板") preview = item else itemEditor = item }) {
                            Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 14.dp, bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (selecting) Checkbox(checked = item.id in selection, onCheckedChange = { checked -> selection = if (checked) selection + item.id else selection - item.id })
                                Box(Modifier.size(if (item.kind == "项目模板") 62.dp else 44.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = .11f), RoundedCornerShape(if (item.kind == "项目模板") 12.dp else 50.dp)), contentAlignment = Alignment.Center) {
                                    Icon(when (item.kind) { "项目模板" -> Icons.Outlined.PhotoLibrary; "引导词库" -> Icons.Outlined.DirectionsWalk; else -> Icons.Outlined.CameraAlt }, null, tint = MaterialTheme.colorScheme.primary)
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(item.title.ifBlank { item.content }, maxLines = 2, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    if (item.kind == "项目模板") Text(tr("{0} 个镜头", item.template?.cards?.size ?: 0), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    else if (item.title.isNotBlank() && item.content.isNotBlank()) Text(item.content, maxLines = 2, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                var showMenu by remember(item.id) { mutableStateOf(false) }
                                if (!selecting) Box {
                                    IconButton(onClick = { showMenu = true }) { Icon(Icons.Outlined.MoreVert, tr("资源操作")) }
                                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                        if (item.kind == "项目模板") DropdownMenuItem(text = { Text(tr("使用模板新建项目")) }, onClick = {
                                            item.template?.let { template -> store.saveProject(projectFromTemplate(template, item.title)); notice = "已从模板新建项目" } ?: run { notice = "模板内容不可用" }; showMenu = false
                                        })
                                        if (item.kind == "项目模板") DropdownMenuItem(text = { Text(tr("复制模板")) }, onClick = { store.saveResource(item.copy(id = freshId(), title = item.title + " 副本")); showMenu = false })
                                        DropdownMenuItem(text = { Text(tr("编辑与收藏")) }, onClick = { itemEditor = item; showMenu = false })
                                        DropdownMenuItem(text = { Text(tr("加入其他收藏夹")) }, onClick = { selection = setOf(item.id); chooseDestination = true; showMenu = false })
                                        if (currentFolder != null && currentFolder.id != "__default__") DropdownMenuItem(text = { Text(tr("从此收藏夹移除")) }, onClick = { store.saveResource(item.copy(folderIds = item.folderIds - currentFolder.id)); showMenu = false })
                                        DropdownMenuItem(text = { Text(tr("删除资源")) }, onClick = { deletingItem = item; showMenu = false })
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (selecting && selection.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 10.dp)) {
                    item { Button(onClick = { chooseDestination = true }) { Text(tr("存到其他收藏夹")) } }
                    if (currentFolder != null && currentFolder.id != "__default__") item { OutlinedButton(onClick = { store.resources.filter { it.id in selection }.toList().forEach { store.saveResource(removeFromFolder(it, currentFolder.id)) }; finishSelection() }) { Text(tr("从此收藏夹移除")) } }
                    item { OutlinedButton(onClick = { confirmBatchDelete = true }) { Text(tr("删除")) } }
                }
            }
            }
        }
    }
    if (chooseDestination) AlertDialog(onDismissRequest = { chooseDestination = false }, title = { Text(tr("存到其他收藏夹")) }, text = {
        Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
            Text(tr("所选内容会保留在当前收藏夹，并同时保存到目标收藏夹。"), style = MaterialTheme.typography.bodySmall)
            val targets = folders.filter { it.id != currentFolder?.id }
            if (targets.isEmpty()) Text(tr("没有其他收藏夹，请先创建。"), modifier = Modifier.padding(vertical = 12.dp))
            targets.forEach { folder -> TextButton(onClick = { store.resources.filter { it.id in selection }.toList().forEach { resource -> if (folder.id !in resource.folderIds) store.saveResource(addToFolder(resource, folder.id)) }; chooseDestination = false; finishSelection() }) { Text(folder.name) } }
        }
    }, confirmButton = { TextButton(onClick = { chooseDestination = false }) { Text(tr("取消")) } })
    if (confirmBatchDelete) AlertDialog(onDismissRequest = { confirmBatchDelete = false }, title = { Text(tr("删除 {0} 项资源？", selection.size)) }, text = { Text(tr("资源会从整个资源库和所有收藏夹中删除，不会影响已插入项目的内容。")) }, confirmButton = { TextButton(onClick = { selection.toList().forEach { store.deleteResource(it) }; confirmBatchDelete = false; finishSelection() }) { Text(tr("删除")) } }, dismissButton = { TextButton(onClick = { confirmBatchDelete = false }) { Text(tr("取消")) } })
    if (newFolder || folderEditor != null) FolderEditor(folderEditor, onDismiss = { newFolder = false; folderEditor = null }) { name ->
        store.saveFolder(folderEditor?.copy(name = name) ?: LibraryFolder(id = freshId(), kind = kind, name = name, sortOrder = (folders.maxOfOrNull { it.sortOrder } ?: -1) + 1)); newFolder = false; folderEditor = null
    }
    deletingFolder?.let { folder ->
        AlertDialog(onDismissRequest = { deletingFolder = null }, title = { Text(tr("删除收藏夹？")) }, text = { Text(tr("只删除收藏夹及收藏关系，资源仍会保留。")) },
            confirmButton = { TextButton(onClick = { store.deleteFolder(folder.id); if (folderId == folder.id) folderId = null; deletingFolder = null }) { Text(tr("删除")) } }, dismissButton = { TextButton(onClick = { deletingFolder = null }) { Text(tr("取消")) } })
    }
    deletingItem?.let { item ->
        AlertDialog(onDismissRequest = { deletingItem = null }, title = { Text(tr("删除资源？")) }, text = { Text(tr("资源将从全部资源和所有收藏夹中删除。")) },
            confirmButton = { TextButton(onClick = { store.deleteResource(item.id); deletingItem = null }) { Text(tr("删除")) } }, dismissButton = { TextButton(onClick = { deletingItem = null }) { Text(tr("取消")) } })
    }
    if (newItem || itemEditor != null) LibraryEditor(store, itemEditor, kind, folders, currentFolder?.id?.takeUnless { it == "__default__" }, onDismiss = { newItem = false; itemEditor = null }, onSave = { value -> store.saveResource(value); if (newItem && value.kind == "项目模板") preview = value; newItem = false; itemEditor = null })
    preview?.let { item -> TemplateDetail(item.id, store, onDismiss = { preview = null }) }
    notice?.let { AlertDialog(onDismissRequest = { notice = null }, text = { Text(trMessage(it)) }, confirmButton = { TextButton(onClick = { notice = null }) { Text(tr("好")) } }) }
}

@Composable
private fun LibraryFolderTile(name: String, count: Int, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.size(width = 82.dp, height = 76.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = .11f), RoundedCornerShape(17.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Folder, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp)) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) { Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold); Text(tr("{0} 个内容", count), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun TemplateSceneEditor(project: Project, onDismiss: () -> Unit, onChange: (Project) -> Unit) {
    var name by remember { mutableStateOf("") }
    var rename by remember { mutableStateOf<String?>(null) }
    var delete by remember { mutableStateOf<String?>(null) }
    val scenes = (project.scenes + project.cards.map { it.scene }.filter { it.isNotBlank() }).distinct()
    val valid = name.isNotBlank() && scenes.none { it == name.trim() && it != rename }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(tr("场景管理")) }, text = {
        Column(Modifier.fillMaxWidth().heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(name, { name = it }, label = { Text(if (rename == null) tr("新场景") else tr("重命名场景")) }, modifier = Modifier.weight(1f), singleLine = true)
                TextButton(enabled = valid, onClick = {
                    val old = rename; val value = name.trim()
                    val updated = if (old == null) scenes + value else scenes.map { if (it == old) value else it }
                    onChange(project.copy(scenes = updated, cards = if (old == null) project.cards else project.cards.map { if (it.scene == old) it.copy(scene = value) else it }))
                    name = ""; rename = null
                }) { Text(if (rename == null) tr("添加") else tr("保存")) }
            }
            if (rename != null) TextButton(onClick = { rename = null; name = "" }) { Text(tr("取消重命名")) }
            scenes.forEachIndexed { index, scene ->
                HorizontalDivider(); Text(scene, style = MaterialTheme.typography.titleSmall)
                Row {
                    TextButton(enabled = index > 0, onClick = { val order = scenes.toMutableList(); java.util.Collections.swap(order, index, index - 1); onChange(project.copy(scenes = order)) }) { Text(tr("上移")) }
                    TextButton(enabled = index < scenes.lastIndex, onClick = { val order = scenes.toMutableList(); java.util.Collections.swap(order, index, index + 1); onChange(project.copy(scenes = order)) }) { Text(tr("下移")) }
                    TextButton(onClick = { rename = scene; name = scene }) { Text(tr("重命名")) }
                    TextButton(onClick = { delete = scene }) { Text(tr("删除")) }
                }
            }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text(tr("完成")) } })
    delete?.let { target -> AlertDialog(onDismissRequest = { delete = null }, title = { Text(tr("删除场景？")) }, text = { Text(tr("脚本将移到未分组，内容会保留。")) }, confirmButton = { TextButton(onClick = { onChange(project.copy(scenes = scenes.filterNot { it == target }, cards = project.cards.map { if (it.scene == target) it.copy(scene = "") else it })); delete = null }) { Text(tr("删除")) } }, dismissButton = { TextButton(onClick = { delete = null }) { Text(tr("取消")) } }) }
}

@Composable
private fun FolderEditor(folder: LibraryFolder?, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember(folder?.id) { mutableStateOf(folder?.name.orEmpty()) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (folder == null) tr("新建收藏夹") else tr("重命名收藏夹")) }, text = { OutlinedTextField(name, { name = it }, label = { Text(tr("收藏夹名称")) }, singleLine = true) }, confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onSave(name.trim()) }) { Text(tr("保存")) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(tr("取消")) } })
}

@Composable
private fun LibraryEditor(store: Store, item: LibraryItem?, kind: String, folders: List<LibraryFolder>, initialFolderId: String?, onDismiss: () -> Unit, onSave: (LibraryItem) -> Unit) {
    var title by remember(item?.id) { mutableStateOf(item?.title.orEmpty()) }
    var content by remember(item?.id) { mutableStateOf(item?.content.orEmpty()) }
    var selected by remember(item?.id) { mutableStateOf(item?.folderIds?.toSet() ?: setOfNotNull(initialFolderId)) }
    if (kind == "项目模板") {
        TemplateInfoEditor(item ?: LibraryItem(kind = kind, folderIds = selected.toList(), template = Project()), store, onDismiss, onSave)
        return
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (item == null) tr("新增{0}", tr(kind)) else tr("编辑资源")) }, text = {
        Column(Modifier.fillMaxWidth().heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (kind == "项目模板") {
                OutlinedTextField(title, { title = it }, label = { Text(tr("模板名称")) }, modifier = Modifier.fillMaxWidth())
                Text(tr("模板脚本和场景可在模板详情中编辑。"), style = MaterialTheme.typography.bodySmall)
            } else OutlinedTextField(content, { content = it }, label = { Text(if (kind == "引导词库") tr("动作与引导") else tr("拍摄方法")) }, minLines = 3, modifier = Modifier.fillMaxWidth())
            if (folders.isNotEmpty()) {
                Text(tr("收藏夹"), style = MaterialTheme.typography.titleSmall)
                folders.forEach { folder ->
                    Row(Modifier.fillMaxWidth().clickable { selected = if (folder.id in selected) selected - folder.id else selected + folder.id }) {
                        Checkbox(checked = folder.id in selected, onCheckedChange = { checked -> selected = if (checked) selected + folder.id else selected - folder.id })
                        Text(folder.name, modifier = Modifier.padding(top = 12.dp))
                    }
                }
            }
        }
    }, confirmButton = { TextButton(enabled = if (kind == "项目模板") title.isNotBlank() else content.isNotBlank(), onClick = {
        onSave((item ?: LibraryItem(id = freshId(), kind = kind)).copy(title = title.trim(), content = content.trim(), folderIds = selected.toList()))
    }) { Text(tr("保存")) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(tr("取消")) } })
}
