// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Hiroshi33XX and contributors.
package com.shotegy.android

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun ProfileScreen(store: Store, appearance: String, onAppearanceChange: (String) -> Unit) {
    val context = LocalContext.current
    var dialog by remember { mutableStateOf<String?>(null) }
    val appearanceLabel = when (appearance) { "light" -> "浅色"; "dark" -> "深色"; else -> "跟随系统" }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        MainPageHeader(tr("我的"))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF5E70E8), Color(0xFFA180EF))), RoundedCornerShape(20.dp)).clickable { dialog = "pro" }.heightIn(min = 104.dp).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(Icons.Outlined.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(28.dp))
            Column(Modifier.weight(1f)) { Text(tr("拍序 Pro"), color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text(tr("了解专业版"), color = Color.White.copy(alpha = .86f), style = MaterialTheme.typography.bodyMedium) }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = Color.White)
        }
        BackupControls(store)
        ProfileSection("外观与语言") {
            ProfileRow("外观", appearanceLabel, Icons.Outlined.LightMode) { dialog = "appearance" }
            HorizontalDivider(Modifier.padding(start = 68.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
            ProfileRow("语言", LocalAppLanguage.current.displayName, Icons.Outlined.Language) { dialog = "language" }
        }
        ProfileSection("其他") {
            ProfileRow("分享给好友", null, Icons.Outlined.IosShare) {
                val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, context.localized("推荐一款本地优先的人像摄影策划工具：拍序 Shotegy。"))
                context.startActivity(Intent.createChooser(intent, context.localized("分享")))
            }
            HorizontalDivider(Modifier.padding(start = 68.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
            ProfileRow("关于拍序", null, Icons.Outlined.Info) { dialog = "about" }
        }
        Spacer(Modifier.height(100.dp))
        }
    }
    when (dialog) {
        "appearance" -> AlertDialog(onDismissRequest = { dialog = null }, title = { Text(tr("外观")) }, text = { Column { listOf("system" to "跟随系统", "light" to "浅色", "dark" to "深色").forEach { (value, label) -> Row(Modifier.fillMaxWidth().clickable { onAppearanceChange(value); dialog = null }.height(52.dp), verticalAlignment = Alignment.CenterVertically) { Text(tr(label), Modifier.weight(1f)); if (value == appearance) Text("✓", color = MaterialTheme.colorScheme.primary) } } } }, confirmButton = { TextButton(onClick = { dialog = null }) { Text(tr("完成")) } })
        "pro" -> AlertDialog(onDismissRequest = { dialog = null }, title = { Text(tr("拍序 Pro")) }, text = { Text(tr("Android 版本的购买与跨平台权益尚未接入。")) }, confirmButton = { TextButton(onClick = { dialog = null }) { Text(tr("好")) } })
        "language" -> LanguageSettings { dialog = null }
        "about" -> AboutScreen { dialog = null }
    }
}

@Composable
internal fun ProfileSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
        Column { Text(tr(title), Modifier.padding(start = 20.dp, top = 18.dp, bottom = 8.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); content() }
    }
}

@Composable
internal fun ProfileRow(title: String, subtitle: String?, icon: ImageVector, enabled: Boolean = true, loading: Boolean = false, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).padding(horizontal = 18.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(36.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = .11f), CircleShape), contentAlignment = Alignment.Center) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(19.dp)) }
        Column(Modifier.weight(1f)) { Text(tr(title), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold); if (subtitle != null) Text(tr(subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
