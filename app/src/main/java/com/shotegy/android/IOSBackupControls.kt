// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Hiroshi33XX and contributors.
package com.shotegy.android

import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.io.File
import kotlin.concurrent.thread

@Composable
internal fun rememberBackupExporter(store: Store): (String) -> Unit {
    val context=androidx.compose.ui.platform.LocalContext.current
    var working by remember{mutableStateOf(false)}
    var error by remember{mutableStateOf<String?>(null)}
    val active=remember{java.util.concurrent.atomic.AtomicBoolean(true)}
    DisposableEffect(Unit){onDispose{active.set(false)}}
    if(working) AlertDialog(onDismissRequest={},title={Text(tr("正在导出…"))},text={LinearProgressIndicator(Modifier.fillMaxWidth())},confirmButton={})
    error?.let{message->AlertDialog(onDismissRequest={error=null},title={Text(tr("操作失败"))},text={Text(trMessage(message))},confirmButton={TextButton(onClick={error=null}){Text(tr("好"))}})}
    return { kind ->
        if(!working&&!store.isReadFailed){
            val ps=store.projects.toList();val rs=store.resources.toList();val fs=store.folders.toList();working=true
            thread(name="shotegy-data-export"){
                val result=runCatching{
                    val directory=newExportDirectory(context)
                    val stamp=java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
                    val file=File(directory,if(kind=="library")context.localized("拍序资源库_{0}.shotegy-library",stamp) else context.localized("拍序完整备份_{0}.shotegy-backup",stamp))
                    file.outputStream().use{IOSBackup.write(it,store,ps,rs,fs,kind)};file
                }
                Handler(Looper.getMainLooper()).post{if(active.get()){working=false;result.onSuccess{file->runCatching{shareExportFiles(context,listOf(file),"application/octet-stream")}.onFailure{error=it.localizedMessage}}.onFailure{error=it.localizedMessage}}}
            }
        }
    }
}

@Composable
internal fun IOSBackupControls(store: Store) {
    val context=androidx.compose.ui.platform.LocalContext.current
    var working by remember{mutableStateOf(false)}
    var message by remember{mutableStateOf<String?>(null)}
    var pending by remember{mutableStateOf<PreparedIOSImport?>(null)}
    val active=remember{java.util.concurrent.atomic.AtomicBoolean(true)}
    val owner=remember{mutableListOf<PreparedIOSImport>()}
    val export=rememberBackupExporter(store)
    DisposableEffect(Unit){onDispose{active.set(false);owner.forEach{it.close()};owner.clear()}}
    val importer=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null){
        working=true
        thread(name="shotegy-ios-import"){
            val result=runCatching{val input=context.contentResolver.openInputStream(uri)?:error("无法读取文件");IOSBackup.prepare(input,context.cacheDir,store)}
            Handler(Looper.getMainLooper()).post{if(active.get()){working=false;result.onSuccess{owner.add(it);pending=it}.onFailure{message="导入失败：${it.localizedMessage}"}}else result.getOrNull()?.close()}
        }
    }}
    ProfileSection("数据备份"){
        ProfileRow("完整数据备份","全部项目、图片、机位和资源库",Icons.Outlined.Archive,enabled=!working&&!store.isReadFailed){export("workspace")}
        HorizontalDivider(Modifier.padding(start=68.dp),color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=.5f))
        ProfileRow("导入完整备份","从拍序完整备份恢复全部内容",Icons.Outlined.FolderOpen,enabled=!working&&!store.isReadFailed,loading=working){importer.launch(arrayOf("*/*"))}
    }
    pending?.let{prepared->AlertDialog(onDismissRequest={prepared.close();owner.remove(prepared);pending=null},title={Text(tr("导入备份？"))},text={Text(tr("将追加 {0} 个项目、{1} 项资源及 {2} 个收藏夹。重复导入可能产生副本。", prepared.projects.size, prepared.resources.size, prepared.folders.size))},confirmButton={TextButton(onClick={try{prepared.commit();message="已导入 ${prepared.projects.size} 个项目和 ${prepared.resources.size} 项资源。"}catch(e:Exception){message="导入保存失败：${e.localizedMessage}"}finally{prepared.close();owner.remove(prepared);pending=null}}){Text(tr("追加导入"))}},dismissButton={TextButton(onClick={prepared.close();owner.remove(prepared);pending=null}){Text(tr("取消"))}})}
    message?.let{value->AlertDialog(onDismissRequest={message=null},title={Text(tr("数据备份"))},text={Text(trMessage(value))},confirmButton={TextButton(onClick={message=null}){Text(tr("好"))}})}
}
