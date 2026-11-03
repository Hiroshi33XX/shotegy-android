// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Hiroshi33XX and contributors.
package com.shotegy.android

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import android.graphics.BitmapFactory
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import android.app.DatePickerDialog
import java.util.UUID
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

private fun newId() = UUID.randomUUID().toString()

@Composable
fun ProjectsScreen(store: Store, initialProjectId: String? = null, onCloseProject: (() -> Unit)? = null) {
    var selectedId by remember(initialProjectId) { mutableStateOf(initialProjectId) }
    var editing by remember { mutableStateOf<Project?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Project?>(null) }
    var editingCard by remember { mutableStateOf<ShotCard?>(null) }
    var addingCard by remember { mutableStateOf(false) }
    var deletingCard by remember { mutableStateOf<ShotCard?>(null) }
    var shooting by remember { mutableStateOf(false) }
    var styling by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    var sceneManager by remember { mutableStateOf(false) }
    val displayPrefs = LocalContext.current.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)
    var visualCards by remember { mutableStateOf(displayPrefs.getBoolean("visualCards", false)) }
    var reorderScripts by remember { mutableStateOf(false) }
    var confirmSceneOrder by remember { mutableStateOf(false) }
    var previousOrder by remember { mutableStateOf<List<String>?>(null) }
    var groupByScene by remember { mutableStateOf(false) }
    var groupByScale by remember { mutableStateOf(false) }
    var cardFilter by remember { mutableStateOf("全部") }
    var projectFilter by remember { mutableStateOf("近期") }
    var batchEditing by remember { mutableStateOf(false) }
    var selectedProjects by remember { mutableStateOf(setOf<String>()) }
    var bulkDelete by remember { mutableStateOf(false) }
    var createMenu by remember { mutableStateOf(false) }
    var chooseTemplate by remember { mutableStateOf(false) }
    val selected = store.projects.firstOrNull { it.id == selectedId }
    val setTabsVisible = LocalSetTabVisibility.current
    LaunchedEffect(selected?.id) { setTabsVisible(selected == null) }
    DisposableEffect(Unit) { onDispose { setTabsVisible(true) } }
    BackHandler(enabled = selectedId != null) { if (shooting) shooting = false else if (styling) styling = false else if (exporting) exporting = false else if (onCloseProject != null) onCloseProject() else selectedId = null }

    if (selected != null && shooting) {
        ShootingScreen(selected, store, onClose = { shooting = false })
    } else if (selected != null && styling) {
        StylingEditor(selected, store, onSave = { store.saveProject(it); styling = false }, onDismiss = { styling = false })

    } else if (selected != null && exporting) {
        ProjectExport(selected, store, onDismiss = { exporting = false })
    } else if (selected == null) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            MainPageHeader(if (batchEditing) tr("已选择 {0} 项", selectedProjects.size) else tr("项目"), inset = 0.dp) {
                TextButton(onClick = { batchEditing = !batchEditing; selectedProjects = emptySet() }) { Text(if (batchEditing) tr("完成") else tr("批量")) }
                if (!batchEditing) Box {
                    IconButton(onClick = { createMenu = true }, modifier = Modifier.background(MaterialTheme.colorScheme.surface, CircleShape)) { Icon(Icons.Outlined.Add, tr("新建项目"), tint = MaterialTheme.colorScheme.primary) }
                    DropdownMenu(expanded = createMenu, onDismissRequest = { createMenu = false }) {
                        DropdownMenuItem(text = { Text(tr("新建空白项目")) }, onClick = { createMenu = false; creating = true })
                        DropdownMenuItem(text = { Text(tr("从项目模板新建")) }, onClick = { createMenu = false; chooseTemplate = true })
                    }
                }
            }
            Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), RoundedCornerShape(9.dp)).padding(2.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                listOf("近期", "未完成", "已完成", "归档").forEach { filter ->
                    Box(Modifier.weight(1f).background(if (projectFilter == filter) MaterialTheme.colorScheme.surface else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(7.dp)).clickable { projectFilter = filter; selectedProjects = emptySet() }.padding(vertical = 7.dp), contentAlignment = Alignment.Center) { Text(tr(filter), style = MaterialTheme.typography.labelLarge, maxLines = 1) }
                }
            }
            Spacer(Modifier.height(10.dp))
            val visibleProjects = store.projects.filter { when (projectFilter) {
                "归档" -> it.archived
                "已完成" -> !it.archived && it.status == "已完成"
                "未完成" -> !it.archived && it.status != "已完成"
                else -> !it.archived && it.status != "已完成" && runCatching { val date = LocalDate.parse(it.date); date >= LocalDate.now().minusDays(7) && date <= LocalDate.now().plusDays(7) }.getOrDefault(false)
            } }.sortedByDescending { it.updatedAt }
            if (visibleProjects.isEmpty()) {
                Column(Modifier.fillMaxSize().padding(horizontal = 32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.PhotoLibrary, null, Modifier.size(42.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    Text(if (store.projects.isEmpty()) tr("建立第一份拍摄项目") else tr("这里还没有项目"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(if (store.projects.isEmpty()) tr("从项目信息到拍摄脚本，把准备工作集中在一个地方。") else tr("切换筛选条件，或新建一份项目。"), modifier = Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    if (store.projects.isEmpty()) Button(onClick = {
                        val first = ShotCard(guidance = "拿花向前走，听到提示后回头看镜头", technique = "使用长焦压缩背景，保留暖色逆光轮廓", startTime = "17:10", endTime = "17:35")
                        val second = ShotCard(guidance = "坐在岸边整理头发，再望向水面", startTime = "17:40", endTime = "18:10")
                        store.saveProject(Project(name = "河畔黄昏人像", theme = "夏日河畔", style = "自然、松弛、暖色逆光", clientName = "示例客户", date = LocalDate.now().plusDays(4).toString(), startTime = "17:00", endTime = "19:00", location = "滨江公园", wardrobe = "浅色连衣裙", props = "鲜花", status = "待拍摄", cards = listOf(first, second)))
                    }, modifier = Modifier.padding(top = 16.dp)) { Text(tr("载入示例项目")) }
                }
            } else {
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 100.dp)) {
                    items(visibleProjects, key = { it.id }) { project ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (batchEditing) Checkbox(checked = project.id in selectedProjects, onCheckedChange = { checked -> selectedProjects = if (checked) selectedProjects + project.id else selectedProjects - project.id })
                            Box(Modifier.weight(1f)) { ProjectListCard(project, store, onOpen = { if (batchEditing) selectedProjects = if (project.id in selectedProjects) selectedProjects - project.id else selectedProjects + project.id else selectedId = project.id }, onShoot = { if (!batchEditing) { selectedId = project.id; shooting = true } }, showShoot = !batchEditing) }
                        }
                    }
                }
                if (batchEditing && selectedProjects.isNotEmpty()) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    TextButton(onClick = { store.projects.filter { it.id in selectedProjects }.toList().forEach { store.saveProject(it.copy(status = "已完成")) }; selectedProjects = emptySet(); batchEditing = false }) { Text(tr("标记完成")) }
                    TextButton(onClick = { store.projects.filter { it.id in selectedProjects }.toList().forEach { store.saveProject(it.copy(archived = projectFilter != "归档")) }; selectedProjects = emptySet(); batchEditing = false }) { Text(if (projectFilter == "归档") tr("取消归档") else tr("归档")) }
                    TextButton(onClick = { bulkDelete = true }) { Text(tr("删除"), color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    } else {
        Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 100.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                NavigationHeader(tr("拍摄脚本"), leading = { TextButton(onClick = { if (onCloseProject != null) onCloseProject() else selectedId = null }) { Text(tr("‹ 返回")) } }) {
                    var menu by remember { mutableStateOf(false) }
                    Row { IconButton(enabled = !selected.archived, onClick = { addingCard = true }) { Icon(Icons.Outlined.Add, tr("添加拍摄脚本"), tint = MaterialTheme.colorScheme.primary) }
                    Box { IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreHoriz, tr("项目操作"), tint = MaterialTheme.colorScheme.primary) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text(tr("编辑项目信息")) }, onClick = { menu = false; editing = selected })
                            DropdownMenuItem(text = { Text(tr("导出项目")) }, onClick = { menu = false; exporting = true })
                            DropdownMenuItem(text = { Text(tr("存为项目模板")) }, onClick = { menu = false; store.saveResource(LibraryItem(kind = "项目模板", title = selected.name, template = templateSnapshot(selected))) })
                            DropdownMenuItem(text = { Text(if (selected.archived) tr("取消归档") else tr("归档项目")) }, onClick = { menu = false; store.saveProject(selected.copy(archived = !selected.archived)) })
                            DropdownMenuItem(text = { Text(tr("删除项目")) }, onClick = { menu = false; deleting = selected })
                        }
                    } }
                }
                ProjectOverview(selected, store, onDetails = { editing = selected }, onStyling = { styling = true })
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { Text(tr("拍摄脚本"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold); Text("${selected.cards.count { cardFilter == "全部" || it.status == cardFilter }}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant); if (cardFilter != "全部") Text(tr(cardFilter), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }
                    var scriptMenu by remember { mutableStateOf(false) }
                    Box { TextButton(onClick = { scriptMenu = true }) { Text(tr("脚本选项")) }
                        DropdownMenu(expanded = scriptMenu, onDismissRequest = { scriptMenu = false }) {
                            listOf("全部", "未拍", "已完成", "已跳过").forEach { filter -> DropdownMenuItem(text = { Text((if (cardFilter == filter) "✓ " else "") + filter) }, onClick = { cardFilter = filter; scriptMenu = false }) }
                            if (!groupByScene && !groupByScale) listOf(false to "紧凑卡片", true to "大图卡片").forEach { (visual, title) -> DropdownMenuItem(text = { Text((if (visualCards == visual) "✓ " else "") + title) }, onClick = { visualCards = visual; displayPrefs.edit().putBoolean("visualCards", visual).apply(); scriptMenu = false }) }
                            DropdownMenuItem(enabled = !selected.archived && selected.cards.size > 1, text = { Text(tr("调整拍摄脚本顺序")) }, onClick = { reorderScripts = true; scriptMenu = false })
                            if (groupByScene) {
                                DropdownMenuItem(enabled = !selected.archived && selected.cards.size > 1, text = { Text(tr("按场景重排脚本")) }, onClick = { confirmSceneOrder = true; scriptMenu = false })
                                if (previousOrder != null) DropdownMenuItem(text = { Text(tr("撤销脚本重排")) }, onClick = { val ranks = previousOrder!!.withIndex().associate { it.value to it.index }; store.saveProject(selected.copy(cards = selected.cards.sortedBy { ranks[it.id] ?: Int.MAX_VALUE })); previousOrder = null; scriptMenu = false })
                            }
                            DropdownMenuItem(enabled = !selected.archived, text = { Text(tr("管理小场景")) }, onClick = { sceneManager = true; scriptMenu = false })
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), RoundedCornerShape(9.dp)).padding(2.dp)) {
                    listOf("拍摄顺序", "景别", "小场景").forEachIndexed { mode, label ->
                        val active = if (mode == 0) !groupByScene && !groupByScale else if (mode == 1) groupByScale else groupByScene
                        Box(Modifier.weight(1f).background(if (active) MaterialTheme.colorScheme.surface else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(7.dp)).clickable { groupByScale = mode == 1; groupByScene = mode == 2 }.padding(vertical = 7.dp), contentAlignment = Alignment.Center) { Text(tr(label), style = MaterialTheme.typography.labelLarge) }
                    }
                }
            }
            if (selected.cards.isEmpty()) item { Text(tr("添加镜头卡片，记录拍摄指引和技巧。"), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            val filteredCards = selected.cards.filter { cardFilter == "全部" || it.status == cardFilter }
            if (groupByScene || groupByScale) {
                item { ScriptGroupedGallery(selected, filteredCards, groupByScene, store, onSelect = { editingCard = it }, onAdd = { scene, scale -> editingCard = ShotCard(scene = scene, shotScale = scale) }) }
            } else {
            items(filteredCards, key = { it.id }) { card ->
                val index = selected.cards.indexOfFirst { it.id == card.id }
                if (visualCards) ScriptVisualCard(card, index, store) { if (!selected.archived) editingCard = card }
                else ScriptCompactCard(card, index) { if (!selected.archived) editingCard = card }
            }
            }
        }
        if (selected.cards.isNotEmpty() && !selected.archived) Button(onClick = { store.saveProject(selected.copy(status = "拍摄中")); shooting = true }, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp), contentPadding = PaddingValues(horizontal = 28.dp, vertical = 12.dp)) { Text(tr("进入拍摄模式")) }
        }
    }
    if (chooseTemplate) AlertDialog(onDismissRequest = { chooseTemplate = false }, title = { Text(tr("从项目模板新建")) }, text = {
        val templates = store.resources.filter { it.kind == "项目模板" && it.template != null }
        if (templates.isEmpty()) Text(tr("还没有项目模板，可在项目中选择“存为项目模板”。"))
        else Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
            templates.forEach { item -> TextButton(onClick = { item.template?.let { source -> val project = projectFromTemplate(source, item.title); store.saveProject(project); selectedId = project.id }; chooseTemplate = false }) { Text(item.title.ifBlank { tr("未命名模板") }) } }
        }
    }, confirmButton = { TextButton(onClick = { chooseTemplate = false }) { Text(tr("关闭")) } })
    if (bulkDelete) AlertDialog(onDismissRequest = { bulkDelete = false }, title = { Text(tr("删除 {0} 个项目？", selectedProjects.size)) }, text = { Text(tr("所选项目中的拍摄脚本和媒体记录将一并移除。")) }, confirmButton = { TextButton(onClick = { selectedProjects.forEach(store::deleteProject); selectedProjects = emptySet(); batchEditing = false; bulkDelete = false }) { Text(tr("删除项目")) } }, dismissButton = { TextButton(onClick = { bulkDelete = false }) { Text(tr("取消")) } })
    if (reorderScripts && selected != null) ScriptReorderEditor(selected, store, onDismiss = { reorderScripts = false })
    if (confirmSceneOrder && selected != null) AlertDialog(onDismissRequest = { confirmSceneOrder = false }, title = { Text(tr("按场景重排脚本？")) }, text = { Text(tr("拍摄顺序会按小场景的排列重新整理，可以撤销本次重排。")) }, confirmButton = { TextButton(onClick = { previousOrder = selected.cards.map { it.id }; val scenes = (selected.scenes + selected.cards.map { it.scene }.filter { it.isNotBlank() }).distinct(); store.saveProject(selected.copy(cards = selected.cards.sortedBy { scenes.indexOf(it.scene).takeIf { i -> i >= 0 } ?: Int.MAX_VALUE })); confirmSceneOrder = false }) { Text(tr("重排")) } }, dismissButton = { TextButton(onClick = { confirmSceneOrder = false }) { Text(tr("取消")) } })
    if (sceneManager && selected != null) SceneManager(selected, store, onDismiss = { sceneManager = false })
    if (creating || editing != null) {
        ProjectEditor(store, editing, onDismiss = { creating = false; editing = null }, onSave = {
            store.saveProject(it); creating = false; editing = null
        })
    }
    deleting?.let { project ->
        AlertDialog(onDismissRequest = { deleting = null }, title = { Text(tr("删除项目？")) }, text = { Text(tr("“{0}”及其镜头将被删除。", project.name)) }, confirmButton = { TextButton(onClick = { store.deleteProject(project.id); selectedId = null; deleting = null; onCloseProject?.invoke() }) { Text(tr("删除")) } }, dismissButton = { TextButton(onClick = { deleting = null }) { Text(tr("取消")) } })
    }
    if ((addingCard || editingCard != null) && selected != null) {
        ShotEditor(store, selected, editingCard, onUpdate = { card ->
            val latest = store.projects.firstOrNull { it.id == selected.id } ?: selected
            store.saveProject(latest.copy(cards = if (latest.cards.any { it.id == card.id }) latest.cards.map { if (it.id == card.id) card else it } else latest.cards + card))
        }, onDelete = { card -> val latest = store.projects.firstOrNull { it.id == selected.id } ?: selected; store.saveProject(latest.copy(cards = latest.cards.filterNot { it.id == card.id })); addingCard = false; editingCard = null }, onDismiss = { addingCard = false; editingCard = null }, onSave = { card ->
            val current = store.projects.firstOrNull { it.id == selected.id } ?: selected
            val cards = if (current.cards.any { it.id == card.id }) current.cards.map { if (it.id == card.id) card else it } else current.cards + card
            store.saveProject(current.copy(cards = cards)); addingCard = false; editingCard = null
        })
    }
    deletingCard?.let { card ->
        AlertDialog(onDismissRequest = { deletingCard = null }, title = { Text(tr("删除镜头？")) }, text = { Text(tr("此操作将移除这张镜头卡片。")) }, confirmButton = { TextButton(onClick = { selected?.let { store.saveProject(it.copy(cards = it.cards.filterNot { c -> c.id == card.id })) }; deletingCard = null }) { Text(tr("删除")) } }, dismissButton = { TextButton(onClick = { deletingCard = null }) { Text(tr("取消")) } })
    }
}

@Composable
private fun ProjectCover(project: Project, store: Store, size: androidx.compose.ui.unit.Dp) {
    val imageId = project.cards.firstNotNullOfOrNull { it.referenceImages.firstOrNull() }
    val bitmap = remember(imageId) {
        imageId?.let { store.referenceImage(it) }?.let { file ->
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            var sample = 1
            while (bounds.outWidth / sample > 400 || bounds.outHeight / sample > 400) sample *= 2
            BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
        }
    }
    Box(Modifier.size(size).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)), contentAlignment = Alignment.Center) {
        if (bitmap == null) Icon(Icons.Outlined.PhotoLibrary, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        else Image(bitmap, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}

@Composable
private fun ProjectListCard(project: Project, store: Store, onOpen: () -> Unit, onShoot: () -> Unit, showShoot: Boolean = true) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth().clickable(onClick = onOpen), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                ProjectCover(project, store, 86.dp)
                Column(Modifier.height(86.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(project.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(project.theme.ifBlank { project.style.ifBlank { tr("尚未填写主题") } }, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.weight(1f))
                    Text(listOf(project.date, listOf(project.startTime, project.endTime).filter { it.isNotBlank() }.joinToString(" – "), project.location).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            val finished = project.cards.count { it.status == "已完成" }
            val processed = project.cards.count { it.status != "未拍" }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LinearProgressIndicator(progress = { if (project.cards.isEmpty()) 0f else processed.toFloat() / project.cards.size }, modifier = Modifier.weight(1f))
                Text("$finished/${project.cards.size}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (showShoot && !project.archived && project.status != "已完成" && project.cards.isNotEmpty()) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { TextButton(onClick = onShoot, contentPadding = PaddingValues(0.dp)) { Text(tr("开始拍摄")) } }
        }
    }
}

@Composable
internal fun ProjectOverview(project: Project, store: Store, onDetails: () -> Unit, onStyling: () -> Unit, templateMode: Boolean = false) {
    val metrics = if (templateMode) listOf("脚本" to project.cards.size, "参考图" to project.cards.sumOf { it.referenceImages.size }) else listOf("脚本" to project.cards.size, "已拍" to project.cards.count { it.status == "已完成" }, "跳过" to project.cards.count { it.status == "已跳过" }, "未拍" to project.cards.count { it.status == "未拍" })
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
        Column {
            Column(Modifier.clickable(onClick = onDetails).padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (project.cards.any { it.referenceImages.isNotEmpty() }) ProjectCover(project, store, 48.dp)
                    Column(Modifier.weight(1f)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Text(project.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis); if (!templateMode) Text(tr(project.status), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        val subtitle = listOf(project.theme, project.style).filter { it.isNotBlank() }.joinToString(" · ")
                        if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val metadata = if (templateMode) project.props.takeIf { it.isNotBlank() }?.let { tr("道具 · {0}", it) }.orEmpty() else listOf(project.date, project.location).filter { it.isNotBlank() }.joinToString(" · ")
                        if (metadata.isNotBlank()) Text(metadata, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(Modifier.fillMaxWidth()) { metrics.forEach { (label, value) -> Column(Modifier.weight(1f)) { Text("$value", style = MaterialTheme.typography.labelLarge); Text(tr(label), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
            }
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            Row(Modifier.fillMaxWidth().clickable(onClick = onStyling).padding(horizontal = 16.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text(tr("造型与道具"), style = MaterialTheme.typography.titleSmall); Text(if (project.clothingStyle.isBlank()) tr("添加服装、妆发和配色") else if (!project.clothingStyleIsCustom) tr(project.clothingStyle) else project.clothingStyle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(tr(label), Modifier.width(56.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value.ifBlank { "—" })
    }
}

@Composable
private fun ProjectEditor(store: Store, project: Project?, onDismiss: () -> Unit, onSave: (Project) -> Unit) {
    val context = LocalContext.current
    var name by remember(project) { mutableStateOf(project?.name.orEmpty()) }
    var theme by remember(project) { mutableStateOf(project?.theme.orEmpty()) }
    var style by remember(project) { mutableStateOf(project?.style.orEmpty()) }
    var client by remember(project) { mutableStateOf(project?.clientName.orEmpty()) }
    var modelStyle by remember(project) { mutableStateOf(project?.modelStyle.orEmpty()) }
    var weather by remember(project) { mutableStateOf(project?.desiredWeather.orEmpty()) }
    var equipment by remember(project) { mutableStateOf(project?.equipment.orEmpty()) }
    var date by remember(project) { mutableStateOf(project?.date.orEmpty()) }
    var location by remember(project) { mutableStateOf(project?.location.orEmpty()) }
    var notes by remember(project) { mutableStateOf(project?.notes.orEmpty()) }
    var startTime by remember(project) { mutableStateOf(project?.startTime.orEmpty()) }
    var endTime by remember(project) { mutableStateOf(project?.endTime.orEmpty()) }
    var contact by remember(project) { mutableStateOf(project?.contact.orEmpty()) }
    var wardrobe by remember(project) { mutableStateOf(project?.wardrobe.orEmpty()) }
    var makeup by remember(project) { mutableStateOf(project?.makeup.orEmpty()) }
    var hair by remember(project) { mutableStateOf(project?.hair.orEmpty()) }
    var props by remember(project) { mutableStateOf(project?.props.orEmpty()) }
    var status by remember(project) { mutableStateOf(project?.status?.takeIf { it in projectStatuses } ?: "草稿") }
    var stylingDraft by remember(project) { mutableStateOf(project ?: Project()) }
    var showStyling by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    val validDate = date.isBlank() || isValidProjectDate(date)
    val validTime = validTimeRange(startTime, endTime) && (date.isNotBlank() || (startTime.isBlank() && endTime.isBlank()))
    FullScreenDialog(onDismissRequest = onDismiss) {
      Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
       Column {
        NavigationHeader(if (project == null) tr("新建项目") else tr("编辑项目信息"), modifier = Modifier.statusBarsPadding(), leading = { TextButton(onClick = onDismiss) { Text(tr("取消")) } }) {
            TextButton(enabled = name.isNotBlank() && validDate && validTime, onClick = { onSave((project ?: Project()).copy(name = name.trim(), theme = theme, style = style, clientName = client, modelStyle = modelStyle, desiredWeather = weather, equipment = equipment, date = date.trim(), startTime = startTime, endTime = endTime, location = location, contact = contact, wardrobe = stylingDraft.wardrobe, makeup = stylingDraft.makeup, hair = stylingDraft.hair, props = stylingDraft.props, clothingStyle = stylingDraft.clothingStyle, accessories = stylingDraft.accessories, shoes = stylingDraft.shoes, clothingColors = stylingDraft.clothingColors, sceneColor = stylingDraft.sceneColor, stylingImages = stylingDraft.stylingImages, clothingStyleIsCustom = stylingDraft.clothingStyleIsCustom, notes = notes, status = status)) }) { Text(tr("保存")) }
        }
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FormSection("项目概况") {
                FormText(name, { name = it }, "项目名称"); FormDivider()
                FormText(theme, { theme = it }, "主题"); FormDivider()
                FormText(style, { style = it }, "风格", true)
            }
            FormSection("人物") {
                FormText(client, { client = it }, "客户"); FormDivider()
                FormText(contact, { contact = it }, "联系方式"); FormDivider()
                FormText(modelStyle, { modelStyle = it }, "模特风格", true)
            }
            FormSection("时间与环境") {
                FormSwitch("设置拍摄日期", date.isNotBlank()) { if (it) date = LocalDate.now().toString() else { date = ""; startTime = ""; endTime = "" } }
                if (date.isNotBlank()) {
                    FormDivider(); FormMonthCalendar(date, store.projects) { date = it }; FormDivider()
                    FormSwitch("设置拍摄时间", startTime.isNotBlank() || endTime.isNotBlank()) { if (it) { startTime = "14:00"; endTime = "18:00" } else { startTime = ""; endTime = "" } }
                    if (startTime.isNotBlank() || endTime.isNotBlank()) {
                        FormDivider(); FormAction("开始时间", startTime.ifBlank { tr("未设置") }) { showTime = true }
                        FormDivider(); FormAction("结束时间", endTime.ifBlank { tr("未设置") }) { showTime = true }
                        store.projects.filter { it.id != project?.id && !it.archived && it.date == date && it.startTime.isNotBlank() && it.endTime.isNotBlank() && startTime < it.endTime && endTime > it.startTime }.forEach { Text(tr("与“{0}” {1}–{2} 时间重叠。", it.name, it.startTime, it.endTime), Modifier.padding(horizontal = 16.dp, vertical = 6.dp), color = androidx.compose.ui.graphics.Color(0xFFCE7E27), style = MaterialTheme.typography.bodySmall) }
                    }
                }
                FormDivider(); FormText(location, { location = it }, "地点")
                FormDivider(); FormText(weather, { weather = it }, "希望天气", true)
            }
            FormSection("拍摄器材") { FormText(equipment, { equipment = it }, "器材", true) }
            FormSection("造型与道具") { FormAction("造型方案", listOf(stylingDraft.clothingStyle, stylingDraft.wardrobe).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { tr("尚未设置") }) { showStyling = true } }
            FormSection("项目备注") { FormText(notes, { notes = it }, "项目备注", true) }
            Spacer(Modifier.height(24.dp))
        }
       }
      }
    }
    if (showTime) FormTimeEditor(startTime, endTime, { from, to -> startTime = from; endTime = to; showTime = false }, { showTime = false })
    if (showStyling) StylingEditor(stylingDraft.copy(theme = theme, style = style, modelStyle = modelStyle, desiredWeather = weather, equipment = equipment, notes = notes), store, onSave = { stylingDraft = it; theme = it.theme; style = it.style; modelStyle = it.modelStyle; weather = it.desiredWeather; equipment = it.equipment; notes = it.notes; showStyling = false }, onDismiss = { showStyling = false })

}

@Composable
internal fun ShotEditor(store: Store, project: Project, card: ShotCard?, onDismiss: () -> Unit, onSave: (ShotCard) -> Unit, allowScouts: Boolean = true, onUpdate: ((ShotCard) -> Unit)? = null, onDelete: ((ShotCard) -> Unit)? = null) {
    var scene by remember(card) { mutableStateOf(card?.scene.orEmpty()) }
    var guidance by remember(card) { mutableStateOf(card?.guidanceEntries ?: emptyList()) }
    var technique by remember(card) { mutableStateOf(card?.techniqueEntries ?: emptyList()) }
    var startTime by remember(card) { mutableStateOf(card?.startTime.orEmpty()) }
    var endTime by remember(card) { mutableStateOf(card?.endTime.orEmpty()) }
    var timeNote by remember(card) { mutableStateOf(card?.timeNote.orEmpty()) }
    var shotScale by remember(card) { mutableStateOf(card?.shotScale.orEmpty()) }
    var referenceImages by remember(card) { mutableStateOf(card?.referenceImages ?: emptyList()) }
    var scouts by remember(card) { mutableStateOf(card?.scouts ?: emptyList()) }
    var photoKind by remember { mutableStateOf("参考图") }
    var manageScenes by remember { mutableStateOf(false) }
    var editTime by remember { mutableStateOf(false) }
    var editingScout by remember { mutableStateOf<Scout?>(null) }
    var chooseKind by remember(card) { mutableStateOf<String?>(null) }
    var status by remember(card) { mutableStateOf(card?.status?.takeIf { it in shotStatuses } ?: "未拍") }
    val stableId = remember { card?.id ?: newId() }
    val draft = (card ?: ShotCard(id = stableId)).copy(guidance = guidance.filter { it.text.isNotBlank() }.joinToString("\n") { it.text }, technique = technique.filter { it.text.isNotBlank() }.joinToString("\n") { it.text }, guidanceItems = guidance.filter { it.text.isNotBlank() }, techniqueItems = technique.filter { it.text.isNotBlank() }, scene = scene, status = status, startTime = startTime, endTime = endTime, timeNote = timeNote, shotScale = shotScale, referenceImages = referenceImages, scouts = scouts)
    var previousDraft by remember { mutableStateOf(draft) }
    var actions by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(draft) { if (draft != previousDraft && validTimeRange(startTime, endTime)) { onUpdate?.invoke(draft); previousDraft = draft } }
    fun finish() { if (validTimeRange(startTime, endTime)) onSave(draft) }
    FullScreenDialog(onDismissRequest = { finish() }) {
      Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
       Column(Modifier.fillMaxSize()) {
        NavigationHeader(tr("编辑拍摄脚本"), modifier = Modifier.statusBarsPadding(), leading = { TextButton(onClick = { finish() }) { Text(tr("‹ 返回")) } }) {
            Box { IconButton(onClick = { actions = true }) { Icon(Icons.Outlined.MoreHoriz, tr("脚本操作")) }
                DropdownMenu(actions, { actions = false }) {
                    DropdownMenuItem(text = { Text(tr("复制拍摄脚本")) }, onClick = { onUpdate?.invoke(draft); onSave(draft.copy(id = newId(), scouts = emptyList(), guidanceItems = draft.guidanceEntries.map { it.copy(id = newId()) }, techniqueItems = draft.techniqueEntries.map { it.copy(id = newId()) })); actions = false })
                    if (onDelete != null) DropdownMenuItem(text = { Text(tr("删除拍摄脚本")) }, onClick = { actions = false; confirmDelete = true })
                }
            }
        }
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FormSection("分类") {
                FormPicker("景别", shotScale, listOf("") + scriptScales) { shotScale = it }; FormDivider()
                FormPicker("小场景", scene, listOf("") + (project.scenes + listOf(scene).filter { it.isNotBlank() }).distinct()) { scene = it }; if (allowScouts) FormDivider()
                if (allowScouts) FormAction("管理小场景") { manageScenes = true }
            }
            FormSection(if (allowScouts) "图片" else "参考图") {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (allowScouts) FormSegments(if (allowScouts) listOf("参考图 ${referenceImages.size}", "踩点图 ${scouts.size}") else listOf("参考图 ${referenceImages.size}"), if (photoKind == "参考图") "参考图 ${referenceImages.size}" else "踩点图 ${scouts.size}") { photoKind = if (it.startsWith("参考图")) "参考图" else "踩点图" }
                    if (photoKind == "参考图") ReferenceImagesEditor(store, referenceImages) { referenceImages = it }
                    else ScoutImagesEditor(store, scouts, onEdit = { editingScout = it }, onDelete = { scout -> scouts = scouts.filterNot { it.id == scout.id } })
                }
            }
            PhraseEditor("动作与引导", guidance, onLibrary = { chooseKind = "引导词库" }, onStore = { store.saveResource(LibraryItem(kind = "引导词库", content = it)) }) { guidance = it }
            PhraseEditor("拍摄方法", technique, onLibrary = { chooseKind = "方法库" }, onStore = { store.saveResource(LibraryItem(kind = "方法库", content = it)) }) { technique = it }
            FormSection("建议时间") {
                FormAction("时间", listOf(startTime, endTime).filter { it.isNotBlank() }.joinToString(" – ").ifBlank { tr("未设置") }) { editTime = true }; FormDivider()
                FormText(timeNote, { timeNote = it }, "时间备注", true)
            }
            if (allowScouts) FormSection("拍摄状态") { Box(Modifier.padding(12.dp)) { FormSegments(shotStatuses, status) { status = it } } }
            Spacer(Modifier.height(20.dp))
        }
       }
      }
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text(tr("删除这项拍摄脚本？")) }, text = { Text(tr("短句、参考图和踩点记录将一并删除。")) }, confirmButton = { TextButton(onClick = { onDelete?.invoke(draft); confirmDelete = false }) { Text(tr("删除")) } }, dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(tr("取消")) } })
    if (manageScenes) SceneManager(project, store) { manageScenes = false }
    if (editTime) FormTimeEditor(startTime, endTime, { from, to -> startTime = from; endTime = to; editTime = false }, { editTime = false })
    editingScout?.let { selectedScout -> ScoutRecordEditor(project, store, selectedScout, onSave = { saved -> scouts = if (scouts.any { it.id == saved.id }) scouts.map { if (it.id == saved.id) saved else it } else scouts + saved; editingScout = null }, onDismiss = { editingScout = null }, onPlanApplied = { plan, zoneId ->
        val zone = zoneId?.let { runCatching { java.time.ZoneId.of(it) }.getOrNull() } ?: java.time.ZoneId.systemDefault()
        val formatter = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
        plan.recommendedStart?.let { startTime = java.time.Instant.ofEpochMilli(it).atZone(zone).format(formatter) }
        plan.recommendedEnd?.let { endTime = java.time.Instant.ofEpochMilli(it).atZone(zone).format(formatter) }
        timeNote = "${plan.lightType} · 太阳方向预测"
    }) }
    chooseKind?.let { kind ->
        AlertDialog(onDismissRequest = { chooseKind = null }, title = { Text(tr(kind)) }, text = {
            val entries = store.resources.filter { it.kind == kind && it.content.isNotBlank() }
            if (entries.isEmpty()) Text(tr("资源库中还没有词条")) else Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                entries.forEach { item -> TextButton(onClick = {
                    if (kind == "引导词库") guidance = guidance + PhraseItem(text = item.content)
                    else technique = technique + PhraseItem(text = item.content)
                    chooseKind = null
                }) { Text(item.content) } }
            }
        }, confirmButton = { TextButton(onClick = { chooseKind = null }) { Text(tr("关闭")) } })
    }
}

@Composable
private fun PhraseEditor(title: String, phrases: List<PhraseItem>, onLibrary: () -> Unit, onStore: (String) -> Unit, onChange: (List<PhraseItem>) -> Unit) {
    var editingId by remember { mutableStateOf<String?>(null) }
    var text by remember { mutableStateOf("") }
    FormSection(title, action = { TextButton(onClick = { editingId = "new"; text = "" }, contentPadding = PaddingValues(0.dp)) { Text(tr("＋ 添加")) } }) {
        if (phrases.isEmpty()) Text(tr("尚未添加内容"), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        phrases.forEachIndexed { index, item ->
            var menu by remember(item.id) { mutableStateOf(false) }
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(item.text, Modifier.weight(1f).clickable { editingId = item.id; text = item.text }.padding(vertical = 13.dp))
                Box { IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreHoriz, tr("短句操作")) }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(text = { Text(tr("编辑")) }, onClick = { menu = false; editingId = item.id; text = item.text })
                        DropdownMenuItem(text = { Text(tr("复制")) }, onClick = { onChange(phrases.toMutableList().apply { add(index + 1, item.copy(id = newId())) }); menu = false })
                        DropdownMenuItem(text = { Text(tr("保存到资源库")) }, onClick = { onStore(item.text); menu = false })
                        if (index > 0) DropdownMenuItem(text = { Text(tr("上移")) }, onClick = { onChange(phrases.toMutableList().apply { java.util.Collections.swap(this, index, index - 1) }); menu = false })
                        if (index < phrases.lastIndex) DropdownMenuItem(text = { Text(tr("下移")) }, onClick = { onChange(phrases.toMutableList().apply { java.util.Collections.swap(this, index, index + 1) }); menu = false })
                        DropdownMenuItem(text = { Text(tr("删除")) }, onClick = { onChange(phrases.filterNot { it.id == item.id }); menu = false })
                    }
                }
            }
            FormDivider()
        }
        FormAction("从资源库选择", onClick = onLibrary)
    }
    if (editingId != null) AlertDialog(onDismissRequest = { editingId = null }, title = { Text(tr(title)) }, text = { FormText(text, { text = it }, "输入内容", true) }, confirmButton = { TextButton(enabled = text.isNotBlank(), onClick = { onChange(if (editingId == "new") phrases + PhraseItem(text = text.trim()) else phrases.map { if (it.id == editingId) it.copy(text = text.trim()) else it }); editingId = null }) { Text(tr("保存")) } }, dismissButton = { TextButton(onClick = { editingId = null }) { Text(tr("取消")) } })
}

@Composable
private fun EditField(value: String, update: (String) -> Unit, label: String) {
    OutlinedTextField(value = value, onValueChange = update, label = { Text(tr(label)) }, modifier = Modifier.fillMaxWidth())
}


private val projectStatuses = listOf("草稿", "待拍摄", "拍摄中", "已完成")
private fun validTimeRange(start: String, end: String): Boolean {
    if (start.isBlank() && end.isBlank()) return true
    val format = DateTimeFormatter.ofPattern("HH:mm").withResolverStyle(ResolverStyle.STRICT)
    return try { java.time.LocalTime.parse(start, format) < java.time.LocalTime.parse(end, format) } catch (_: Exception) { false }
}
internal val shotStatuses = listOf("未拍", "已完成", "已跳过")
private fun isValidProjectDate(value: String): Boolean {
    if (!Regex("\\d{4}-\\d{2}-\\d{2}").matches(value)) return false
    return try {
        LocalDate.parse(value, DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT))
        true
    } catch (_: Exception) { false }
}

@Composable
private fun StatusPicker(value: String, options: List<String>, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text(tr("状态：{0}  ▾", tr(value))) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(tr(option)) }, onClick = { onChange(option); expanded = false })
            }
        }
    }
}
