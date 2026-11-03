// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Hiroshi33XX and contributors.
package com.shotegy.android

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import java.util.Locale

private val styles = linkedMapOf(
    "日系 / 日常" to listOf("纯色连衣裙", "衬衫 + 半裙", "背心裙", "针织衫 / 针织外套", "短袖或卫衣 + 半裙"),
    "森系 / 小清新" to listOf("棉麻长裙", "碎花裙", "棉麻衬衫", "长半裙", "针织开衫", "蕾丝 / 田园元素", "层叠穿搭"),
    "复古 / 古着" to listOf("古着连衣裙", "特殊花纹毛衣", "复古衬衫", "高腰半裙", "格纹裙", "复古外套"),
    "制服 / 学院" to listOf("衬衫 + 半裙", "背心裙", "水手服", "制服裙", "西装外套", "针织背心"),
    "旗袍" to listOf("传统旗袍", "宽松旗袍", "修身旗袍", "改良旗袍"),
    "新中式" to listOf("盘扣上衣", "斜襟 / 对襟上衣", "立领上衣", "新中式长裙", "现代剪裁中式套装")
)
private fun normalizeHex(value: String): String? {
    val text = value.trim().removePrefix("#").uppercase(Locale.ROOT)
    return if (text.matches(Regex("[0-9A-F]{6}"))) "#$text" else null
}
private fun hexColor(hex: String): Color = Color(android.graphics.Color.parseColor(normalizeHex(hex) ?: "#92928D"))
private fun recommendations(hex: String, offsets: List<Float>): List<String> {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(android.graphics.Color.parseColor(normalizeHex(hex) ?: "#677D52"), hsv)
    return offsets.map { offset ->
        val adjusted = floatArrayOf((hsv[0] + offset + 360f) % 360f, (hsv[1] * .75f).coerceIn(.25f, .7f), hsv[2].coerceIn(.65f, .9f))
        String.format(Locale.ROOT, "#%06X", android.graphics.Color.HSVToColor(adjusted) and 0xFFFFFF)
    }
}

@Composable
fun StylingEditor(project: Project, store: Store, onSave: (Project) -> Unit, onDismiss: () -> Unit) {
    var style by remember(project.id) { mutableStateOf(project.clothingStyle) }
    var custom by remember(project.id) { mutableStateOf(project.clothingStyleIsCustom) }
    var wardrobe by remember(project.id) { mutableStateOf(project.wardrobe) }
    var makeup by remember(project.id) { mutableStateOf(project.makeup) }
    var hair by remember(project.id) { mutableStateOf(project.hair) }
    var accessories by remember(project.id) { mutableStateOf(project.accessories) }
    var shoes by remember(project.id) { mutableStateOf(project.shoes) }
    var props by remember(project.id) { mutableStateOf(project.props) }
    var colors by remember(project.id) { mutableStateOf(project.clothingColors) }
    var images by remember(project.id) { mutableStateOf(project.stylingImages) }
    var scene by remember(project.id) { mutableStateOf(project.sceneColor) }
    var showColors by remember { mutableStateOf(false) }
    var newColor by remember { mutableStateOf(false) }
    var editingColor by remember { mutableStateOf<String?>(null) }
    FullScreenDialog(onDismissRequest = onDismiss) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
          Column(Modifier.fillMaxSize()) {
            NavigationHeader(tr("造型与道具"), leading = { TextButton(onClick = onDismiss) { Text(tr("取消")) } }) {
                TextButton(onClick = { onSave(project.copy(clothingStyle = style.trim(), clothingStyleIsCustom = custom, wardrobe = wardrobe.trim(), makeup = makeup.trim(), hair = hair.trim(), accessories = accessories.trim(), shoes = shoes.trim(), props = props.trim(), clothingColors = colors, sceneColor = normalizeHex(scene).orEmpty(), stylingImages = images)); onDismiss() }) { Text(tr("保存")) }
            }
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
              Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(tr("服装"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            var menu by remember { mutableStateOf(false) }
            Box {
                OutlinedButton(onClick = { menu = true }) { Text(if (style.isBlank()) tr("选择风格") else if (!custom) tr(style) else style) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    styles.keys.forEach { preset -> DropdownMenuItem(text = { Text(tr(preset)) }, onClick = { style = preset; custom = false; menu = false }) }
                    DropdownMenuItem(text = { Text(tr("自定义")) }, onClick = { custom = true; menu = false })
                }
            }
            if (custom) OutlinedTextField(style, { style = it; custom = true }, label = { Text(tr("服装风格")) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(wardrobe, { wardrobe = it }, label = { Text(tr("具体服装")) }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            if (!custom) styles[style]?.let { suggestions ->
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(suggestions) { suggestion -> val label=tr(suggestion); SuggestionChip(onClick = { if (label !in wardrobe.lines()) wardrobe = listOf(wardrobe.trim(), label).filter { it.isNotBlank() }.joinToString("\n") }, label = { Text(label) }) }
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(tr("服装颜色"), style = MaterialTheme.typography.titleSmall)
                TextButton(onClick = { showColors = true }) { Text(tr("配色推荐")) }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(colors) { hex -> ColorSwatch(hex) { editingColor = hex } }
                item { OutlinedButton(onClick = { newColor = true }) { Text("＋") } }
            }
            ReferenceImagesEditor(store, images["clothing"].orEmpty()) { images = images + ("clothing" to it) }
              }
            }
            StylingCategory("妆容", makeup, { makeup = it }, store, images["makeup"].orEmpty()) { images = images + ("makeup" to it) }
            StylingCategory("发型", hair, { hair = it }, store, images["hair"].orEmpty()) { images = images + ("hair" to it) }
            StylingCategory("配饰", accessories, { accessories = it }, store, images["accessories"].orEmpty()) { images = images + ("accessories" to it) }
            StylingCategory("鞋履", shoes, { shoes = it }, store, images["shoes"].orEmpty()) { images = images + ("shoes" to it) }
            StylingCategory("道具", props, { props = it }, store, images["props"].orEmpty()) { images = images + ("props" to it) }
            Spacer(Modifier.height(16.dp))
            }
          }
        }
    }
    if (newColor) {
        var input by remember { mutableStateOf("#F1EBDD") }
        val valid = normalizeHex(input)
        AlertDialog(onDismissRequest = { newColor = false }, title = { Text(tr("添加服装颜色")) }, text = { Column(Modifier.heightIn(max = 510.dp).verticalScroll(rememberScrollState())) { HslColorControls(input) { input = it } } }, confirmButton = { TextButton(enabled = valid != null, onClick = { valid?.let { if (it !in colors) colors = colors + it }; newColor = false }) { Text(tr("添加")) } }, dismissButton = { TextButton(onClick = { newColor = false }) { Text(tr("取消")) } })
    }
    editingColor?.let { original ->
        var input by remember(original) { mutableStateOf(original) }
        val valid = normalizeHex(input)
        AlertDialog(onDismissRequest = { editingColor = null }, title = { Text(tr("编辑服装颜色")) },
            text = { Column(Modifier.heightIn(max = 510.dp).verticalScroll(rememberScrollState())) { HslColorControls(input) { input = it } } },
            confirmButton = { TextButton(enabled = valid != null && (valid == original || valid !in colors), onClick = { valid?.let { hex -> colors = colors.map { if (it == original) hex else it } }; editingColor = null }) { Text(tr("保存")) } },
            dismissButton = { Row { TextButton(onClick = { colors = colors - original; editingColor = null }) { Text(tr("删除")) }; TextButton(onClick = { editingColor = null }) { Text(tr("取消")) } } })
    }
    if (showColors) {
        var sceneDraft by remember { mutableStateOf(scene.ifBlank { "#677D52" }) }
        val context = LocalContext.current
        var photo by remember { mutableStateOf<Uri?>(null) }
        var loading by remember { mutableStateOf(false) }
        var photoError by remember { mutableStateOf<String?>(null) }
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { photo = it }
        LaunchedEffect(photo) {
            val selected = photo ?: return@LaunchedEffect
            loading = true; photoError = null
            try {
                val value = withContext(Dispatchers.IO) { dominantSceneColor(context, selected) }
                if (value == null) photoError = "无法读取照片，请选择另一张图片。" else sceneDraft = value
            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { photoError = "无法读取照片，请选择另一张图片。" }
            finally { loading = false; photo = null }
        }
        AlertDialog(onDismissRequest = { if (!loading) showColors = false }, title = { Text(tr("配色推荐")) }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(enabled = !loading, onClick = { picker.launch("image/*") }) { Text(if (loading) tr("正在取色…") else tr("从相册取色")) }
                if (photoError != null) Text(trMessage(photoError.orEmpty()), color = MaterialTheme.colorScheme.error)
                Text(tr("场景基色"), style = MaterialTheme.typography.titleSmall)
                HslColorControls(sceneDraft) { sceneDraft = it }
                if (normalizeHex(sceneDraft) == null) Text(tr("请输入六位颜色值"), color = MaterialTheme.colorScheme.error)
                if (normalizeHex(sceneDraft) != null) listOf("邻近色" to listOf(-30f, 30f), "互补色" to listOf(180f), "三角配色" to listOf(120f, 240f)).forEach { (label, offsets) ->
                    Text(tr(label), fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { recommendations(sceneDraft, offsets).forEach { hex -> ColorSwatch(hex) { if (hex !in colors) colors = colors + hex; scene = normalizeHex(sceneDraft).orEmpty(); showColors = false } } }
                }
                Text(tr("中性色"), fontWeight = FontWeight.SemiBold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) { items(listOf("#FFFFFF", "#F1EBDD", "#92928D", "#252525", "#BEA886", "#765641")) { hex -> ColorSwatch(hex) { if (hex !in colors) colors = colors + hex; normalizeHex(sceneDraft)?.let { scene = it }; showColors = false } } }
            }
        }, confirmButton = { TextButton(enabled = !loading && normalizeHex(sceneDraft) != null, onClick = { scene = normalizeHex(sceneDraft).orEmpty(); showColors = false }) { Text(tr("完成")) } })
    }
}

@Composable
private fun StylingField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(value, onChange, label = { Text(tr(label)) }, modifier = Modifier.fillMaxWidth())
}
@Composable
private fun StylingCategory(label: String, value: String, onChange: (String) -> Unit, store: Store, images: List<String>, onImagesChange: (List<String>) -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(tr(label), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            StylingField(label, value, onChange)
            ReferenceImagesEditor(store, images, onImagesChange)
        }
    }
}
@Composable
private fun ColorSwatch(hex: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick)) {
        Box(Modifier.size(36.dp).background(hexColor(hex), CircleShape))
        Text(hex, style = MaterialTheme.typography.labelSmall)
    }
}




/** Downsamples the photo and chooses the largest 4-bit RGB bucket, as in iOS. */
private fun dominantSceneColor(context: Context, uri: Uri): String? {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    val boundsStream = resolver.openInputStream(uri) ?: return null
    boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / sample > 160 || bounds.outHeight / sample > 160) sample *= 2
    val image = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) } ?: return null
    try {
        val pixels = IntArray(image.width * image.height)
        image.getPixels(pixels, 0, image.width, 0, 0, image.width, image.height)
        val counts = IntArray(4096); val sumsR = LongArray(4096); val sumsG = LongArray(4096); val sumsB = LongArray(4096)
        for (pixel in pixels) {
            if (android.graphics.Color.alpha(pixel) < 128) continue
            val r = android.graphics.Color.red(pixel); val g = android.graphics.Color.green(pixel); val b = android.graphics.Color.blue(pixel)
            val key = ((r shr 4) shl 8) or ((g shr 4) shl 4) or (b shr 4)
            counts[key]++; sumsR[key] += r; sumsG[key] += g; sumsB[key] += b
        }
        val best = counts.indices.maxWithOrNull(compareBy<Int> { counts[it] }.thenBy { -it }) ?: return null
        val count = counts[best]
        if (count == 0) return null
        return String.format(Locale.ROOT, "#%02X%02X%02X", (sumsR[best] + count / 2) / count, (sumsG[best] + count / 2) / count, (sumsB[best] + count / 2) / count)
    } finally { image.recycle() }
}
