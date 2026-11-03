// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Hiroshi33XX and contributors.
package com.shotegy.android

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

internal data class ShootingSelection(val cardId: String, val imageId: String?)
internal fun shootingReferencePage(card: ShotCard, imageId: String?) = card.referenceImages.indexOf(imageId).coerceAtLeast(0)
internal fun shootingStatusUpdate(project: Project, cardId: String, status: String, locked: Boolean): Project =
    if (locked || status !in shotStatuses || project.cards.none { it.id == cardId }) project
    else project.copy(cards = project.cards.map { if (it.id == cardId) it.copy(status = status) else it })

@Composable
fun ShootingScreen(project: Project, store: Store, onClose: () -> Unit) {
    var onlyPending by remember(project.id) { mutableStateOf(false) }
    var locked by remember(project.id) { mutableStateOf(false) }
    var keepAwake by remember(project.id) { mutableStateOf(false) }
    var expanded by remember(project.id) { mutableStateOf(false) }
    var settings by remember { mutableStateOf(false) }
    var organization by remember(project.id) { mutableStateOf("按卡片") }
    var selection by remember { mutableStateOf<ShootingSelection?>(null) }
    var currentId by remember(project.id) { mutableStateOf(project.lastShotCardId.takeIf { id -> project.cards.any { it.id == id } } ?: project.cards.firstOrNull()?.id) }
    var saveError by remember { mutableStateOf<String?>(null) }
    val view = LocalView.current
    val density = LocalDensity.current
    var viewportHeight by remember { mutableStateOf(0.dp) }
    DisposableEffect(view, keepAwake) {
        val previous = view.keepScreenOn
        view.keepScreenOn = keepAwake
        onDispose { view.keepScreenOn = previous }
    }
    val visible = if (onlyPending) project.cards.filter { it.status == "未拍" } else project.cards
    fun select(card: ShotCard) {
        currentId = card.id
        val latest = store.projects.firstOrNull { it.id == project.id } ?: project
        if (latest.lastShotCardId != card.id || latest.status != "拍摄中") runCatching {
            store.saveProject(latest.copy(lastShotCardId = card.id, status = "拍摄中"))
        }.onFailure { saveError = "无法保存：${it.localizedMessage}" }
    }
    fun setStatus(cardId: String, status: String) {
        val latest = store.projects.firstOrNull { it.id == project.id } ?: project
        val updated = shootingStatusUpdate(latest, cardId, status, locked)
        if (updated != latest) runCatching { store.saveProject(updated) }.onFailure { saveError = "无法保存：${it.localizedMessage}" }
    }

    Column(Modifier.fillMaxSize().onSizeChanged { viewportHeight = with(density) { it.height.toDp() } }) {
        NavigationHeader(project.name, leading = { TextButton(onClick = onClose) { Text(tr("完成")) } }) {
            Box {
                IconButton(onClick = { settings = true }) { Icon(Icons.Outlined.MoreHoriz, tr("拍摄设置"), tint = MaterialTheme.colorScheme.primary) }
                DropdownMenu(settings, { settings = false }) {
                    DropdownMenuItem(text = { Text(tr("仅看未拍")) }, trailingIcon = { if (onlyPending) Icon(Icons.Outlined.Check, null) }, onClick = {
                        onlyPending = !onlyPending
                        currentId = project.cards.firstOrNull { !onlyPending || it.status == "未拍" }?.id
                        settings = false
                    })
                    DropdownMenuItem(text = { Text(tr("锁定编辑")) }, trailingIcon = { if (locked) Icon(Icons.Outlined.Check, null) }, onClick = { locked = !locked; settings = false })
                    DropdownMenuItem(text = { Text(tr("屏幕常亮")) }, trailingIcon = { if (keepAwake) Icon(Icons.Outlined.Check, null) }, onClick = { keepAwake = !keepAwake; settings = false })
                }
            }
        }
        Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            FormSegments(listOf("按卡片", "按景别", "按小场景"), organization) { organization = it }
        }
        if (organization != "按卡片") {
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(20.dp)) {
                item {
                    ScriptGroupedGallery(project, visible, organization == "按小场景", store, onSelect = {}, onAdd = { _, _ -> }, canAdd = false,
                        onImageSelect = { card, image -> select(card); selection = ShootingSelection(card.id, image) })
                }
            }
        } else if (visible.isEmpty()) {
            Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(Icons.Outlined.PhotoCamera, null, Modifier.size(42.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Text(if (onlyPending) tr("所有脚本都已处理") else tr("还没有拍摄脚本"), style = MaterialTheme.typography.titleMedium)
            }
        } else key(onlyPending) {
            ShootingSequence(project, visible, currentId, store, locked, expanded, { expanded = !expanded }, ::select, ::setStatus, Modifier.weight(1f))
        }
    }
    selection?.let { selected ->
        project.cards.firstOrNull { it.id == selected.cardId }?.let { card ->
            key(card.id) { GroupedShootingSheet(card, project.cards.indexOfFirst { it.id == card.id }, selected.imageId, store, locked, (if (viewportHeight > 0.dp) viewportHeight else 600.dp) * .94f, { setStatus(card.id, it) }, { selection = null }) }
        }
    }
    saveError?.let { message -> AlertDialog(onDismissRequest = { saveError = null }, title = { Text(tr("无法保存")) }, text = { Text(trMessage(message)) }, confirmButton = { TextButton(onClick = { saveError = null }) { Text(tr("好")) } }) }
}

@Composable
private fun ShootingSequence(project: Project, cards: List<ShotCard>, currentId: String?, store: Store, locked: Boolean, expanded: Boolean, onExpand: () -> Unit, onSelect: (ShotCard) -> Unit, onStatus: (String, String) -> Unit, modifier: Modifier) {
    val pager = rememberPagerState(initialPage = cards.indexOfFirst { it.id == currentId }.coerceAtLeast(0), pageCount = { cards.size })
    val selected = cards.getOrNull(pager.currentPage) ?: cards.first()
    val latestSelect by rememberUpdatedState(onSelect)
    LaunchedEffect(pager.settledPage, cards.map { it.id }) { cards.getOrNull(pager.settledPage)?.let(latestSelect) }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val panelHeight = if (expanded) minOf((maxHeight * .44f).coerceIn(300.dp, 400.dp), maxHeight * .72f) else minOf(240.dp, maxHeight * .62f)
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), key = { cards[it].id }, verticalAlignment = Alignment.Top) { page ->
            val card = cards[page]
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${project.cards.indexOfFirst { it.id == card.id } + 1}/${project.cards.size}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val time = shootingTime(card)
                    if (time.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.Schedule, null, Modifier.size(16.dp)); Text(time, style = MaterialTheme.typography.bodyMedium) }
                }
                if (card.referenceImages.isEmpty()) {
                    Box(Modifier.weight(1f).fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = panelHeight + 28.dp).clip(RoundedCornerShape(22.dp))) {
                        ScriptImage(store, null, Modifier.fillMaxSize(), "尚未添加参考图")
                    }
                } else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 2.dp, bottom = panelHeight + 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    itemsIndexed(card.referenceImages, key = { index, id -> "$index:$id" }) { _, id -> NaturalReferenceImage(store, id) }
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(panelHeight + 100.dp).align(Alignment.BottomCenter).background(Brush.verticalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.background.copy(alpha = .18f), MaterialTheme.colorScheme.background.copy(alpha = .82f), MaterialTheme.colorScheme.background))))
        Surface(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(panelHeight), shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = .97f), shadowElevation = 8.dp) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable(onClick = onExpand), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.Camera, null, Modifier.size(20.dp))
                    Text(tr("动作与拍摄提示"), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Icon(if (expanded) Icons.Outlined.ExpandMore else Icons.Outlined.ExpandLess, if (expanded) tr("收起提示") else tr("展开提示"), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                FormSegments(shotStatuses, selected.status, enabled = !locked) { onStatus(selected.id, it) }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { ExecutionDetails(selected, collapsed = !expanded) }
            }
        }
    }
}

@Composable
private fun NaturalReferenceImage(store: Store, id: String) {
    val image = rememberReferenceBitmap(store, id)
    if (image != null) Image(image, tr("参考图"), Modifier.fillMaxWidth().aspectRatio(image.width.toFloat() / image.height).clip(RoundedCornerShape(22.dp)), contentScale = ContentScale.FillBounds)
    else ScriptImage(store, id, Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(RoundedCornerShape(22.dp)))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupedShootingSheet(card: ShotCard, index: Int, initialImage: String?, store: Store, locked: Boolean, contentHeight: Dp, onStatus: (String) -> Unit, onClose: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onClose, sheetState = sheet, containerColor = MaterialTheme.colorScheme.background, contentWindowInsets = { WindowInsets.safeDrawing }) {
        Column(Modifier.height(contentHeight)) {
            NavigationHeader(tr("拍摄单元")) { TextButton(onClick = onClose) { Text(tr("完成")) } }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Box {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Card %02d".format(index + 1), style = MaterialTheme.typography.titleMedium, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
                        if (card.scene.isNotBlank()) Text(card.scene, style = MaterialTheme.typography.bodyMedium)
                        if (card.shotScale.isNotBlank()) Text(tr(card.shotScale), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Box {
                    if (card.referenceImages.isEmpty()) ScriptImage(store, null, Modifier.fillMaxWidth().height(220.dp), "尚未添加参考图")
                    else {
                        val pager = rememberPagerState(initialPage = shootingReferencePage(card, initialImage), pageCount = { card.referenceImages.size })
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.fillMaxWidth().height(360.dp)) {
                                HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                                    ScriptImage(store, card.referenceImages[page], Modifier.fillMaxSize().padding(bottom = 28.dp).clip(RoundedCornerShape(22.dp)), crop = false)
                                }
                                Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                    repeat(card.referenceImages.size) { page -> Box(Modifier.size(6.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = if (page == pager.currentPage) .7f else .18f), CircleShape)) }
                                }
                            }
                            Text("${pager.currentPage + 1} / ${card.referenceImages.size}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Box { ExecutionDetails(card) }
                Box { FormSegments(shotStatuses, card.status, enabled = !locked, onChange = onStatus) }
            }
        }
    }
}

private fun shootingTime(card: ShotCard) = listOf(card.startTime, card.endTime).filter { it.isNotBlank() }.joinToString(" – ")

@Composable
private fun ExecutionDetails(card: ShotCard, collapsed: Boolean = false) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(if (collapsed) 10.dp else 18.dp)) {
        ExecutionPhrases("动作与引导", Icons.Outlined.DirectionsWalk, card.guidanceEntries.map { it.text }, collapsed)
        HorizontalDivider()
        ExecutionPhrases("拍摄方法", Icons.Outlined.Camera, card.techniqueEntries.map { it.text }, collapsed)
        if (!collapsed) {
            val time = shootingTime(card)
            if (time.isNotEmpty()) Text(time, style = MaterialTheme.typography.bodyMedium)
            if (card.timeNote.isNotBlank()) Text(card.timeNote, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ExecutionPhrases(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, values: List<String>, collapsed: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(if (collapsed) 5.dp else 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, null, Modifier.size(18.dp))
            Text(tr(title), style = if (collapsed) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        if (collapsed) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(values.firstOrNull() ?: "—", Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
            if (values.size > 1) Text("+${values.size - 1}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else if (values.isEmpty()) Text("—", color = MaterialTheme.colorScheme.onSurfaceVariant)
        else values.forEachIndexed { index, value -> Text("${index + 1}. $value", Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyLarge) }
    }
}
