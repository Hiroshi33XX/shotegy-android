// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Hiroshi33XX and contributors.
package com.shotegy.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.vector.ImageVector

val LocalSetTabVisibility = staticCompositionLocalOf<(Boolean) -> Unit> { {} }

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = Store(applicationContext)
        val settings = getSharedPreferences("settings", MODE_PRIVATE)
        setContent {
            var appLanguage by remember { androidx.compose.runtime.mutableStateOf(AppLanguage.saved(this@MainActivity)) }
            val localeContext = remember(appLanguage) { localizedActivityContext(this@MainActivity, appLanguage.locale) }
            CompositionLocalProvider(LocalAppLanguage provides appLanguage, LocalSetAppLanguage provides { value -> AppLanguage.save(this@MainActivity,value); appLanguage=value }, androidx.compose.ui.platform.LocalContext provides localeContext, androidx.compose.ui.platform.LocalConfiguration provides localeContext.resources.configuration) {
            var appearance by remember { androidx.compose.runtime.mutableStateOf(settings.getString("appearance", "system") ?: "system") }
            val dark = appearance == "dark" || (appearance == "system" && isSystemInDarkTheme())
            MaterialTheme(colorScheme = if (dark) darkColorScheme(primary = Color(0xFF9DA9FF)) else lightColorScheme(primary = Color(0xFF5E70E8), background = Color(0xFFF2F2F7), surface = Color.White)) {
            val systemBarBackground = MaterialTheme.colorScheme.background
            androidx.compose.runtime.SideEffect { configureSystemBars(window, systemBarBackground) }
            CompositionLocalProvider(LocalIndication provides QuietIndication, LocalRippleConfiguration provides null) {
                var selected by rememberSaveable { mutableIntStateOf(0) }
                var showTabs by remember { androidx.compose.runtime.mutableStateOf(true) }
                val labels = listOf("项目", "日程", "资源库", "我的")
                val icons = listOf(Icons.Filled.Folder, Icons.Filled.CalendarMonth, Icons.Filled.GridView, Icons.Filled.AccountCircle)
                if (store.isReadFailed) {
                    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(tr("无法读取本机数据"), style = MaterialTheme.typography.headlineSmall)
                        Text(tr("为保护原有数据，编辑已暂停。"))
                        BackupControls(store)
                    }
                } else CompositionLocalProvider(LocalSetTabVisibility provides { visible -> showTabs = visible }) {
                Scaffold { padding ->
                    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().padding(padding)) {
                        when (selected) {
                            0 -> ProjectsScreen(store)
                            1 -> CalendarScreen(store)
                            2 -> LibraryScreen(store)
                            else -> ProfileScreen(store, appearance) { value -> appearance = value; settings.edit().putString("appearance", value).apply() }
                        }
                        if (showTabs) Box(Modifier.align(Alignment.BottomCenter)) { ShotegyTabBar(labels, icons, selected) { selected = it } }
                    }
                }
                }
            }
            }
            }
        }
    }
}

private object QuietIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = object : Modifier.Node(), DrawModifierNode { override fun ContentDrawScope.draw() { drawContent() } }
    override fun equals(other: Any?) = other === this
    override fun hashCode() = 173
}

@Composable
private fun ShotegyTabBar(labels: List<String>, icons: List<ImageVector>, selected: Int, onSelect: (Int) -> Unit) {
    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 18.dp, end = 18.dp, bottom = 10.dp, top = 6.dp), contentAlignment = Alignment.Center) {
        Surface(shape = RoundedCornerShape(50), shadowElevation = 8.dp, color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().height(64.dp).padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                labels.forEachIndexed { index, label ->
                    val active = selected == index
                    Column(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(50)).background(if (active) MaterialTheme.colorScheme.onSurface.copy(alpha = .08f) else Color.Transparent, RoundedCornerShape(50)).clickable { onSelect(index) }, verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        val tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        Icon(icons[index], contentDescription = tr(label), tint = tint, modifier = Modifier.size(24.dp))
                        Text(tr(label), color = tint, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium)
                    }
                }
            }
        }
    }
}
