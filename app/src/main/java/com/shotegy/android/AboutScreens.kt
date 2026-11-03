// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Hiroshi33XX and contributors.
package com.shotegy.android

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun LanguageSettings(onDismiss:()->Unit) {
    val language=LocalAppLanguage.current;val select=LocalSetAppLanguage.current
    FullScreenDialog(onDismissRequest=onDismiss){Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background){Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()){
        NavigationHeader(tr("语言"),leading={TextButton(onClick=onDismiss){Text(tr("‹ 返回"))}})
        Column(Modifier.padding(16.dp)) { Surface(shape=RoundedCornerShape(12.dp),color=MaterialTheme.colorScheme.surface) { Column {
            AppLanguage.entries.forEachIndexed{index,item->
                if(index>0)FormDivider()
                Row(Modifier.fillMaxWidth().clickable{select(item)}.heightIn(min=48.dp).padding(horizontal=16.dp),verticalAlignment=Alignment.CenterVertically){Text(item.displayName,Modifier.weight(1f));if(item==language)Text("✓",color=MaterialTheme.colorScheme.primary)}
            }
        } } }
    }}}
}
@Composable
internal fun AboutScreen(onDismiss:()->Unit) {
    val context=LocalContext.current
    var licenses by remember{mutableStateOf(false)}
    var appLicense by remember{mutableStateOf(false)}
    val version=remember{context.packageManager.getPackageInfo(context.packageName,0).versionName.orEmpty()}
    FullScreenDialog(onDismissRequest=onDismiss){Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background){Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()){
        NavigationHeader(tr("关于拍序"),leading={TextButton(onClick=onDismiss){Text(tr("‹ 返回"))}})
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
            Surface(shape=RoundedCornerShape(20.dp),color=MaterialTheme.colorScheme.surface){Column(Modifier.fillMaxWidth().padding(vertical=24.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(14.dp)){
                Image(painterResource(R.drawable.shotegy_mark),null,Modifier.size(92.dp))
                Text(tr("拍序 Shotegy"),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
                Text(tr("人像摄影策划助手"),color=MaterialTheme.colorScheme.onSurfaceVariant)
            }}
            Surface(shape=RoundedCornerShape(12.dp),color=MaterialTheme.colorScheme.surface){Column{
                FormAction("源代码") { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,android.net.Uri.parse("https://github.com/Hiroshi33XX/shotegy-android"))) };FormDivider()
                FormAction("开源许可"){appLicense=true};FormDivider()
                FormAction("第三方许可"){licenses=true};FormDivider()
                Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically){Text(tr("版本"),Modifier.weight(1f));Text(version,color=MaterialTheme.colorScheme.onSurfaceVariant)}
            }}
        }
    }}}
    if(licenses) ThirdPartyLicenses { licenses=false }
    if(appLicense) LicenseDocumentScreen("GPL-3.0",listOf("licenses/COPYRIGHT.txt","licenses/GPL-3.0.txt")) { appLicense=false }
}
@Composable
internal fun ThirdPartyLicenses(onDismiss:()->Unit) {
    var font by remember{mutableStateOf(false)}
    var software by remember{mutableStateOf(false)}
    FullScreenDialog(onDismissRequest=onDismiss){Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background){Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()){
        NavigationHeader(tr("第三方许可"),leading={TextButton(onClick=onDismiss){Text(tr("‹ 返回"))}})
        Column(Modifier.padding(horizontal=16.dp)) { FormSection("字体") { FormAction("Noto Serif SC"){font=true} }; FormSection("开源软件") { FormAction("AndroidX / Kotlin / libyuv"){software=true} } }
    }}}
    if(font) FontLicenseScreen { font=false }
    if(software) LicenseDocumentScreen(tr("开源软件"),listOf("licenses/THIRD-PARTY.txt","licenses/Apache-2.0.txt","licenses/libyuv-LICENSE.txt","licenses/UPSTREAM-NOTICES.txt")){software=false}
}
@Composable
internal fun FontLicenseScreen(onDismiss:()->Unit) {
    LicenseDocumentScreen(tr("Noto 字体许可"), listOf("fonts/NOTICE.txt","fonts/OFL.txt"), onDismiss)
}
@Composable
internal fun LicenseDocumentScreen(title:String,paths:List<String>,onDismiss:()->Unit) {
    val context=LocalContext.current
    val documents=remember(paths){paths.joinToString("\n\n"){path->context.assets.open(path).bufferedReader().use{it.readText()}}}
    FullScreenDialog(onDismissRequest=onDismiss){Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background){Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()){
        NavigationHeader(title,leading={TextButton(onClick=onDismiss){Text(tr("‹ 返回"))}})
        SelectionContainer(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)){Text(documents,fontFamily=FontFamily.Monospace,style=MaterialTheme.typography.bodySmall)}
    }}}
}
