// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Hiroshi33XX and contributors.
package com.shotegy.android

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun MainPageHeader(title: String, inset: Dp = 16.dp, actions: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(start=inset,end=inset,top=8.dp,bottom=14.dp).heightIn(min=52.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
        Text(title,Modifier.weight(1f),fontSize=34.sp,lineHeight=41.sp,fontWeight=FontWeight.Bold,maxLines=1,overflow=TextOverflow.Ellipsis)
        Row(verticalAlignment=Alignment.CenterVertically,content=actions)
    }
}

@Composable
internal fun NavigationHeader(title: String, modifier: Modifier = Modifier, leading: @Composable () -> Unit = {}, actions: @Composable RowScope.() -> Unit = {}) {
    var left by remember { mutableIntStateOf(0) };var right by remember { mutableIntStateOf(0) }
    val reserved=with(LocalDensity.current){maxOf(left,right).toDp()}+8.dp
    Box(modifier.fillMaxWidth().heightIn(min=48.dp).padding(horizontal=8.dp)) {
        Text(title,Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal=reserved),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold,textAlign=TextAlign.Center,maxLines=1,overflow=TextOverflow.Ellipsis)
        Box(Modifier.align(Alignment.CenterStart).onSizeChanged{left=it.width}) { leading() }
        Row(Modifier.align(Alignment.CenterEnd).onSizeChanged{right=it.width},verticalAlignment=Alignment.CenterVertically,content=actions)
    }
}
