// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Hiroshi33XX and contributors.
package com.shotegy.android

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.core.content.FileProvider
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.io.File
import java.util.UUID
import kotlin.concurrent.thread

internal fun newExportDirectory(context: Context) = File(context.cacheDir, "exports/${UUID.randomUUID()}").apply { check(mkdirs()) { "无法创建导出文件夹" } }
internal fun shareExportFiles(context: Context, files: List<File>, mime: String) {
    require(files.isNotEmpty() && files.all { it.isFile }) { "没有可分享的文件" }
    val uris = files.map { FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", it) }
    val intent = Intent(if (uris.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE).setType(mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    if (uris.size == 1) intent.putExtra(Intent.EXTRA_STREAM, uris.first()) else intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
    intent.clipData = ClipData.newUri(context.contentResolver, files.first().name, uris.first()).apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
    context.startActivity(Intent.createChooser(intent, context.localized("分享")))
}

@Composable
fun ProjectExport(project: Project, store: Store, onDismiss: () -> Unit) {
    val context=LocalContext.current
    var options by remember(project.id) { mutableStateOf(ProjectExportOptions()) }
    var working by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val active=remember{java.util.concurrent.atomic.AtomicBoolean(true)}
    DisposableEffect(Unit){onDispose{active.set(false)}}
    fun export(kind: ProjectExportKind){
        if(working!=null)return
        val snapshot=project;val settings=options;val directory=runCatching{newExportDirectory(context)}.getOrElse{error=it.localizedMessage;return};working=kind.title
        thread(name="shotegy-project-export"){
            val result=runCatching{ProjectExportService(context,store).export(snapshot,settings,kind,directory)}
            Handler(Looper.getMainLooper()).post{
                if(active.get()){working=null;result.onSuccess{files->runCatching{shareExportFiles(context,files,when(kind){ProjectExportKind.PDF->"application/pdf";ProjectExportKind.PROJECT->"application/octet-stream";else->"image/jpeg"})}.onFailure{error=it.localizedMessage}}.onFailure{error=it.localizedMessage}}
            }
        }
    }
    FullScreenDialog(onDismissRequest={if(working==null)onDismiss()},dismissOnBackPress=working==null){
        Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background){
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()){
                NavigationHeader(tr("导出项目"),leading={TextButton(enabled=working==null,onClick=onDismiss){Text(tr("‹ 返回"))}})
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                    FormSection("导出排版") { Box(Modifier.padding(12.dp)){FormSegments(ExportOrganization.entries.map{it.label},options.organization.label,enabled=working==null){label->val mode=ExportOrganization.entries.first{it.label==label};options=options.copy(organization=mode).organized()}} }
                    FormSection("导出内容"){
                        ExportToggle("显示客户名称",options.showClientName,working==null){options=options.copy(showClientName=it)};FormDivider()
                        ExportToggle("显示拍摄地点",options.showLocation,working==null){options=options.copy(showLocation=it)};FormDivider()
                        ExportToggle("显示拍摄方法",options.showTechniques,working==null&&options.organization==ExportOrganization.SEQUENCE){options=options.copy(showTechniques=it)};FormDivider()
                        ExportToggle("显示踩点图",options.showScouts,working==null&&options.organization==ExportOrganization.SEQUENCE){options=options.copy(showScouts=it)};FormDivider()
                        ExportToggle("显示拍序水印",options.showWatermark,working==null){options=options.copy(showWatermark=it)}
                    }
                    FormSection("交付与分享"){
                        ExportActionRow("导出策划长图","高清长图，过长时自动拆分",Icons.Outlined.PhotoLibrary,working==null){export(ProjectExportKind.LONG_IMAGE)};FormDivider()
                        ExportActionRow("逐条导出脚本图","每个拍摄脚本生成一张图片",Icons.Outlined.Collections,working==null){export(ProjectExportKind.CARD_IMAGES)};FormDivider()
                        ExportActionRow("导出 PDF","适合打印或发给团队",Icons.Outlined.Description,working==null){export(ProjectExportKind.PDF)}
                    }
                    FormSection("项目数据"){ExportActionRow("导出项目文件",".shotegy 文件包含项目数据和本地图片",Icons.Outlined.FolderZip,working==null){export(ProjectExportKind.PROJECT)}}
                    working?.let{title->FormSection(""){Column(Modifier.fillMaxWidth().padding(20.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(10.dp)){CircularProgressIndicator(Modifier.size(24.dp));Text(tr("正在生成{0}…", tr(title)))}}}
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
    error?.let{message->AlertDialog(onDismissRequest={error=null},title={Text(tr("导出失败"))},text={Text(trMessage(message))},confirmButton={TextButton(onClick={error=null}){Text(tr("好"))}})}
}

@Composable
internal fun ExportActionRow(title:String,subtitle:String?,icon:ImageVector,enabled:Boolean=true,onClick:()->Unit){
    Surface(onClick=onClick,enabled=enabled,color=MaterialTheme.colorScheme.surface){
        Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=13.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
            Icon(icon,null,Modifier.size(22.dp),tint=MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)){Text(tr(title),style=MaterialTheme.typography.bodyLarge);if(subtitle!=null)Text(tr(subtitle),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        }
    }
}
@Composable
private fun ExportToggle(label:String,value:Boolean,enabled:Boolean,onChange:(Boolean)->Unit){
    Row(Modifier.fillMaxWidth().padding(horizontal=16.dp),verticalAlignment=Alignment.CenterVertically){Text(tr(label),Modifier.weight(1f),color=MaterialTheme.colorScheme.onSurface.copy(alpha=if(enabled)1f else .38f));Switch(value,onCheckedChange=onChange,enabled=enabled)}
}
