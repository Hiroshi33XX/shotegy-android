// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Hiroshi33XX and contributors.
package com.shotegy.android

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

internal fun templateCreativeInfo(value: Project) = value.copy(clientName = "", contact = "", date = "", startTime = "", endTime = "", location = "", status = "草稿", archived = false, lastShotCardId = "", calendarLabelOverride = null, calendarColorHex = null,
    cards = value.cards.map { it.copy(status = "未拍", scouts = emptyList()) })

@Composable
internal fun TemplateDetail(resourceId: String, store: Store, onDismiss: () -> Unit) {
    val resource = store.resources.firstOrNull { it.id == resourceId } ?: return
    val empty = remember(resourceId) { Project(name = resource.title) }
    val template = resource.template ?: empty
    var organization by remember { mutableStateOf("拍摄顺序") }
    val prefs = LocalContext.current.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)
    var visual by remember { mutableStateOf(prefs.getBoolean("templateVisualCards", false)) }
    var info by remember { mutableStateOf(false) }
    var styling by remember { mutableStateOf(false) }
    var scenes by remember { mutableStateOf(false) }
    var reorder by remember { mutableStateOf(false) }
    var confirmOrder by remember { mutableStateOf(false) }
    var previousOrder by remember { mutableStateOf<List<String>?>(null) }
    var edit by remember { mutableStateOf<ShotCard?>(null) }
    var folders by remember { mutableStateOf(false) }
    var createdProjectId by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    fun save(value: Project) {
        val latest = store.resources.firstOrNull { it.id == resourceId } ?: return
        store.saveResource(latest.copy(template = templateCreativeInfo(value)))
    }
    fun changeCard(card: ShotCard) {
        val latest = store.resources.firstOrNull { it.id == resourceId }?.template ?: template
        save(latest.copy(cards = if (latest.cards.any { it.id == card.id }) latest.cards.map { if (it.id == card.id) card else it } else latest.cards + card))
    }
    FullScreenDialog(onDismissRequest = onDismiss) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 90.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    item {
                        NavigationHeader(tr("拍摄脚本模板"), leading = { TextButton(onClick = onDismiss) { Text(tr("‹ 返回")) } }) {
                            IconButton(onClick = { edit = ShotCard() }) { Icon(Icons.Outlined.Add, tr("新增拍摄脚本"), tint = MaterialTheme.colorScheme.primary) }
                            var open by remember { mutableStateOf(false) }
                            Box {
                                IconButton(onClick = { open = true }) { Icon(Icons.Outlined.MoreHoriz, tr("模板操作"), tint = MaterialTheme.colorScheme.primary) }
                                DropdownMenu(open, { open = false }) {
                                    DropdownMenuItem(text = { Text(tr("编辑模板信息")) }, onClick = { info = true; open = false })
                                    DropdownMenuItem(text = { Text(tr("管理所属收藏夹")) }, onClick = { folders = true; open = false })
                                    DropdownMenuItem(text = { Text(tr("复制模板")) }, onClick = { store.saveResource(resource.copy(id = java.util.UUID.randomUUID().toString(), title = resource.title + " 副本", template = templateSnapshot(template))); open = false })
                                    DropdownMenuItem(text = { Text(tr("删除模板"), color = MaterialTheme.colorScheme.error) }, onClick = { confirmDelete = true; open = false })
                                }
                            }
                        }
                        ProjectOverview(template.copy(name = resource.title.ifBlank { "未命名模板" }), store, onDetails = { info = true }, onStyling = { styling = true }, templateMode = true)
                    }
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(tr("拍摄脚本"), fontWeight = FontWeight.SemiBold); Text("  ${template.cards.size}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                                Spacer(Modifier.weight(1f))
                                var open by remember { mutableStateOf(false) }
                                Box {
                                    TextButton(onClick = { open = true }) { Text(tr("脚本选项")) }
                                    DropdownMenu(open, { open = false }) {
                                        if (organization == "拍摄顺序") listOf(false to "紧凑卡片", true to "大图卡片").forEach { (value, label) -> DropdownMenuItem(text = { Text((if (visual == value) "✓ " else "") + label) }, onClick = { visual = value; prefs.edit().putBoolean("templateVisualCards", value).apply(); open = false }) }
                                        if (organization == "小场景") DropdownMenuItem(text = { Text(tr("管理小场景")) }, onClick = { scenes = true; open = false })
                                        DropdownMenuItem(enabled = template.cards.size > 1, text = { Text(tr("调整拍摄脚本顺序")) }, onClick = { previousOrder = null; reorder = true; open = false })
                                        if (organization == "小场景") {
                                            DropdownMenuItem(enabled = template.cards.size > 1, text = { Text(tr("按场景重排脚本")) }, onClick = { confirmOrder = true; open = false })
                                            if (previousOrder != null) DropdownMenuItem(text = { Text(tr("撤销脚本重排")) }, onClick = { val ids = previousOrder!!; save(template.copy(cards = template.cards.sortedBy { ids.indexOf(it.id).takeIf { i -> i >= 0 } ?: Int.MAX_VALUE })); previousOrder = null; open = false })
                                        }
                                    }
                                }
                            }
                            FormSegments(listOf("拍摄顺序", "景别", "小场景"), organization) { organization = it }
                        }
                    }
                    if (template.cards.isEmpty()) item { TextButton(onClick = { edit = ShotCard() }) { Icon(Icons.Outlined.AddCircleOutline, null); Text(tr("  添加第一项拍摄脚本")) } }
                    else if (organization != "拍摄顺序") item { ScriptGroupedGallery(template, template.cards, organization == "小场景", store, onSelect = { edit = it }, onAdd = { scene, scale -> edit = ShotCard(scene = scene, shotScale = scale) }, templateMode = true) }
                    else itemsIndexed(template.cards, key = { _, card -> card.id }) { index, card ->
                        if (visual) ScriptVisualCard(card, index, store, templateMode = true) { edit = card }
                        else ScriptCompactCard(card, index, templateMode = true) { edit = card }
                    }
                }
                Button(onClick = { val created = projectFromTemplate(template, resource.title); store.saveProject(created); createdProjectId = created.id }, Modifier.align(Alignment.BottomCenter).padding(vertical = 12.dp), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)) { Icon(Icons.Outlined.AddBox, null, Modifier.size(18.dp)); Text(tr("  根据此模板创建项目")) }
            }
        }
    }
    createdProjectId?.let { id -> FullScreenDialog(onDismissRequest = { createdProjectId = null }) { Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { ProjectsScreen(store, id, onCloseProject = { createdProjectId = null }) } } }
    if (info) TemplateInfoEditor(resource, store, onDismiss = { info = false }, onSave = { store.saveResource(it); info = false })
    if (styling) StylingEditor(template, store, onSave = { save(it); styling = false }, onDismiss = { styling = false })
    if (scenes) TemplateSceneEditor(template, onDismiss = { scenes = false }, onChange = { save(it); previousOrder = null })
    if (reorder) ScriptReorderEditor(template, store, onDismiss = { reorder = false }, onSave = { save(template.copy(cards = it)) })
    edit?.let { card -> ShotEditor(store, template, card, allowScouts = false, onUpdate = ::changeCard, onDismiss = { edit = null }, onSave = { changeCard(it); edit = null }, onDelete = { save(template.copy(cards = template.cards.filterNot { c -> c.id == it.id })); edit = null }) }
    if (confirmOrder) AlertDialog(onDismissRequest = { confirmOrder = false }, title = { Text(tr("按场景重排脚本？")) }, confirmButton = { TextButton(onClick = { previousOrder = template.cards.map { it.id }; save(template.copy(cards = template.cards.sortedBy { template.scenes.indexOf(it.scene).takeIf { i -> i >= 0 } ?: Int.MAX_VALUE })); confirmOrder = false }) { Text(tr("重排")) } }, dismissButton = { TextButton(onClick = { confirmOrder = false }) { Text(tr("取消")) } })
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text(tr("删除这个模板？")) }, confirmButton = { TextButton(onClick = { store.deleteResource(resource.id); onDismiss() }) { Text(tr("删除")) } }, dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(tr("取消")) } })
    if (folders) AlertDialog(onDismissRequest = { folders = false }, title = { Text(tr("管理所属收藏夹")) }, text = { Column { store.folders.filter { it.kind == "项目模板" }.forEach { folder -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Checkbox(folder.id in resource.folderIds, onCheckedChange = { checked -> store.saveResource(resource.copy(folderIds = if (checked) (resource.folderIds + folder.id).distinct() else resource.folderIds - folder.id)) }); Text(folder.name) } } } }, confirmButton = { TextButton(onClick = { folders = false }) { Text(tr("完成")) } })
}

@Composable
internal fun ScriptCompactCard(card: ShotCard, index: Int, templateMode: Boolean = false, onClick: () -> Unit) {
    Surface(Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
            Surface(Modifier.size(44.dp), shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = .12f)) { Box(contentAlignment = Alignment.Center) { Text("${index + 1}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) } }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(localizedScriptSummary(card), fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val detail = listOf(tr(card.shotScale), card.scene, listOf(card.startTime, card.endTime).filter { it.isNotBlank() }.joinToString(" – ")) + if (templateMode) emptyList() else listOf(tr(card.status))
                if (detail.any { it.isNotBlank() }) Text(detail.filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun TemplateInfoEditor(resource: LibraryItem, store: Store, onDismiss: () -> Unit, onSave: (LibraryItem) -> Unit) {
    var value by remember(resource.id) { mutableStateOf(templateCreativeInfo(resource.template ?: Project())) }
    var title by remember(resource.id) { mutableStateOf(resource.title) }
    var styling by remember { mutableStateOf(false) }
    FullScreenDialog(onDismissRequest = onDismiss) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
                NavigationHeader(tr("编辑模板信息"), leading = { TextButton(onClick = onDismiss) { Text(tr("取消")) } }) {
                    TextButton(enabled = title.isNotBlank(), onClick = { onSave(resource.copy(title = title.trim(), template = templateCreativeInfo(value.copy(name = title.trim())))) }) { Text(tr("保存")) }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FormSection("项目概况") { FormText(title, { title = it }, "模板名称"); FormDivider(); FormText(value.theme, { value = value.copy(theme = it) }, "主题"); FormDivider(); FormText(value.style, { value = value.copy(style = it) }, "风格", true) }
                    FormSection("项目策划") { FormText(value.modelStyle, { value = value.copy(modelStyle = it) }, "模特风格", true); FormDivider(); FormText(value.desiredWeather, { value = value.copy(desiredWeather = it) }, "希望天气", true); FormDivider(); FormText(value.equipment, { value = value.copy(equipment = it) }, "器材", true) }
                    FormSection("") { FormAction("造型与道具", value.clothingStyle) { styling = true } }
                    FormSection("项目备注") { FormText(value.notes, { value = value.copy(notes = it) }, "备注", true) }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
    if (styling) StylingEditor(value, store, onSave = { value = it; styling = false }, onDismiss = { styling = false })
}
