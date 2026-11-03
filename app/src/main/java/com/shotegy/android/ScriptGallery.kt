// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Hiroshi33XX and contributors.
package com.shotegy.android

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

internal val scriptScales = listOf("近景 / 特写", "中景", "远景")
internal fun scriptSummary(card: ShotCard) = card.guidanceEntries.firstOrNull()?.text ?: card.techniqueEntries.firstOrNull()?.text ?: "尚未添加动作提示"

@Composable
internal fun localizedScriptSummary(card: ShotCard) = card.guidanceEntries.firstOrNull()?.text ?: card.techniqueEntries.firstOrNull()?.text ?: tr("尚未添加动作提示")

@Composable
internal fun rememberReferenceBitmap(store: Store, id: String?) = remember(store, id) { id?.let(store::referenceImage)?.let(::decodeReferenceImage)?.asImageBitmap() }


@Composable
internal fun ScriptImage(store: Store, id: String?, modifier: Modifier, summary: String = "", crop: Boolean = true) {
    val bitmap = rememberReferenceBitmap(store, id)
    Box(modifier.clipToBounds().background(if (bitmap != null && !crop) Color.Transparent else MaterialTheme.colorScheme.onSurface.copy(alpha = .045f)), contentAlignment = Alignment.Center) {
        if (bitmap != null) Image(bitmap, tr("参考图"), Modifier.fillMaxSize(), contentScale = if (crop) ContentScale.Crop else ContentScale.Fit)
        else Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text("▧", color = MaterialTheme.colorScheme.onSurfaceVariant); if (summary.isNotEmpty()) Text(summary, maxLines = 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
internal fun ScriptVisualCard(card: ShotCard, index: Int, store: Store, templateMode: Boolean = false, onClick: () -> Unit) {
    Surface(Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
        Column {
            Box {
                ScriptImage(store, card.referenceImages.firstOrNull(), Modifier.fillMaxWidth().aspectRatio(4f / 3f), crop = true)
                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    (listOf("脚本 %02d".format(index + 1)) + if (templateMode) emptyList() else listOf(card.status)).forEach { Text(trMessage(it), Modifier.background(Color.Black.copy(alpha = .62f), RoundedCornerShape(50)).padding(horizontal = 11.dp, vertical = 7.dp), color = Color.White, style = MaterialTheme.typography.labelMedium) }
                }
            }
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                val time = listOf(card.startTime, card.endTime).filter { it.isNotBlank() }.joinToString(" – ")
                if (time.isNotBlank()) Text("◷ $time", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                Text(localizedScriptSummary(card), maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                card.techniqueEntries.firstOrNull()?.let { Text(it.text, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }
}

private data class GalleryTile(val card: ShotCard, val image: String?, val ratio: Float)
internal fun galleryRowRanges(ratios: List<Float>, cardIds: List<String>, width: Float, target: Float): List<IntRange> {
    val result = mutableListOf<IntRange>(); var start = 0; var used = 0f
    ratios.forEachIndexed { i, ratio ->
        var gap = if (i == start) 0f else if (cardIds[i] == cardIds[i - 1]) 4f else 12f
        if (i > start && used + ratio * target + gap > width) { result.add(start until i); start = i; used = 0f; gap = 0f }
        used += ratio * target + gap
    }
    if (start < ratios.size) result.add(start until ratios.size)
    return result
}

internal data class ScriptGalleryGroup(val name: String, val cards: List<ShotCard>)
internal data class ScriptGallerySection(val name: String, val groups: List<ScriptGalleryGroup>)

/** Mirrors ShotGrouping.sections: retain section order and the original script order. */
internal fun scriptGallerySections(project: Project, cards: List<ShotCard>, byScene: Boolean): List<ScriptGallerySection> {
    val scenes = (project.scenes + cards.map { it.scene }.filter { it.isNotBlank() }).distinct() + if (cards.any { it.scene.isBlank() } || project.scenes.isEmpty()) listOf("") else emptyList()
    val scales = (scriptScales + cards.map { it.shotScale }.filter { it.isNotBlank() }).distinct() + if (cards.any { it.shotScale.isBlank() }) listOf("") else emptyList()
    val ordered = cards.sortedBy { card -> project.cards.indexOfFirst { it.id == card.id }.takeIf { it >= 0 } ?: Int.MAX_VALUE }
    return (if (byScene) scenes else scales).map { section ->
        ScriptGallerySection(section, (if (byScene) scales else scenes).mapNotNull { group ->
            val entries = ordered.filter { if (byScene) it.scene == section && it.shotScale == group else it.shotScale == section && it.scene == group }
            entries.takeIf { it.isNotEmpty() }?.let { ScriptGalleryGroup(group, it) }
        })
    }
}

@Composable
internal fun ScriptGroupedGallery(project: Project, cards: List<ShotCard>, byScene: Boolean, store: Store, onSelect: (ShotCard) -> Unit, onAdd: (String, String) -> Unit, canAdd: Boolean = !project.archived, onImageSelect: ((ShotCard, String?) -> Unit)? = null, templateMode: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        scriptGallerySections(project, cards, byScene).forEach { section ->
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) { Text(if (section.name.isBlank()) tr("未分类") else if (byScene) section.name else tr(section.name), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold); if (canAdd) TextButton(onClick = { onAdd(if (byScene) section.name else "", if (byScene) "" else section.name) }) { Text("⊕") } }
                if (section.groups.isEmpty()) Text(tr("尚未添加拍摄单元"), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                section.groups.forEach { group ->
                    Row(verticalAlignment = Alignment.CenterVertically) { Text(if (group.name.isBlank()) tr("未分类") else if (byScene) tr(group.name) else group.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant); if (canAdd) TextButton(onClick = { onAdd(if (byScene) section.name else group.name, if (byScene) group.name else section.name) }) { Text("＋") } }
                    GalleryRows(project, group.cards, store, templateMode) { card, image -> if (onImageSelect != null) onImageSelect(card, image) else if (!project.archived) onSelect(card) }
                }
            }
        }
    }
}

@Composable
private fun GalleryRows(project: Project, cards: List<ShotCard>, store: Store, templateMode: Boolean, onSelect: (ShotCard, String?) -> Unit) {
    val tiles = remember(cards) { cards.flatMap { card ->
        if (card.referenceImages.isEmpty()) listOf(GalleryTile(card, null, .85f)) else card.referenceImages.map { id ->
            GalleryTile(card, id, store.referenceImage(id)?.let(::referenceImageRatio) ?: .8f)
        }
    } }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val availableWidth = maxWidth.value
        val target = (availableWidth / 4).coerceIn(70f, 108f)
        val ranges = galleryRowRanges(tiles.map { it.ratio }, tiles.map { it.card.id }, availableWidth, target)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { ranges.forEach { range ->
            val gaps = range.drop(1).map { if (tiles[it].card.id == tiles[it - 1].card.id) 4f else 12f }.sum()
            val height = minOf(target, (availableWidth - gaps) / range.sumOf { tiles[it].ratio.toDouble() }.toFloat()).coerceAtLeast(1f)
            Row { range.forEach { i -> val tile = tiles[i]
                if (i != range.first) Spacer(Modifier.width(if (tile.card.id == tiles[i - 1].card.id) 4.dp else 12.dp))
                val tileDescription = tr("拍摄单元 %02d，参考图 %d").format(project.cards.indexOfFirst { it.id == tile.card.id } + 1, (tile.card.referenceImages.indexOf(tile.image) + 1).coerceAtLeast(1))
                Box(Modifier.size((height * tile.ratio).dp, height.dp).clip(RoundedCornerShape(4.dp)).semantics(mergeDescendants = true) {
                    contentDescription = tileDescription
                }.clickable { onSelect(tile.card, tile.image) }) {
                    ScriptImage(store, tile.image, Modifier.fillMaxSize(), localizedScriptSummary(tile.card))
                    Text("%02d %s".format(project.cards.indexOfFirst { it.id == tile.card.id } + 1, if (templateMode) "" else when(tile.card.status) { tr("已完成") -> "✓"; tr("已跳过") -> "↠"; else -> "○" }), Modifier.align(Alignment.BottomStart).padding(3.dp).background(Color.Black.copy(alpha = .55f), RoundedCornerShape(3.dp)).padding(horizontal = 4.dp, vertical = 2.dp), color = Color.White, fontSize = 9.sp, lineHeight = 11.sp)
                }
            } }
        } }
    }
}
