// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Hiroshi33XX and contributors.
package com.shotegy.android

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale

private fun eventColor(project: Project, fallback: Color): Color = runCatching {
    val hex = project.calendarColorHex ?: return fallback
    Color(android.graphics.Color.parseColor(if (hex.startsWith('#')) hex else "#$hex"))
}.getOrDefault(fallback)
private fun eventLabel(project: Project) = project.calendarLabelOverride?.trim()?.takeIf { it.isNotEmpty() } ?: project.name

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(store: Store) {
    val displayLocale = LocalAppLanguage.current.locale
    var opened by rememberSaveable { mutableStateOf<String?>(null) }
    if (opened != null) {
        ProjectsScreen(store, initialProjectId = opened, onCloseProject = { opened = null })
        return
    }
    var monthText by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    var dayText by rememberSaveable { mutableStateOf<String?>(null) }
    var editingLabel by remember { mutableStateOf<String?>(null) }
    var editingTime by remember { mutableStateOf<String?>(null) }
    val month = YearMonth.parse(monthText)
    val firstWeekday = WeekFields.of(displayLocale).firstDayOfWeek.value
    val offset = (month.atDay(1).dayOfWeek.value - firstWeekday + 7) % 7
    val rows = (offset + month.lengthOfMonth() + 6) / 7
    val firstDate = month.atDay(1).minusDays(offset.toLong())
    val scheduled = store.projects.filter { !it.archived && runCatching { LocalDate.parse(it.date) }.isSuccess }
    val accent = MaterialTheme.colorScheme.primary
    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        val rowHeight = ((maxHeight - 100.dp) / rows).coerceAtLeast(68.dp)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth().height(62.dp).padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(tr("{0}年{1}月", month.year, month.monthValue), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = { monthText = YearMonth.now().toString() }) { Text(tr("今天")) }
                IconButton(onClick = { monthText = month.minusMonths(1).toString() }) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, tr("上个月")) }
                IconButton(onClick = { monthText = month.plusMonths(1).toString() }) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, tr("下个月")) }
            }
            Row(Modifier.fillMaxWidth().height(38.dp), verticalAlignment = Alignment.CenterVertically) {
                repeat(7) { index ->
                    val day = java.time.DayOfWeek.of((firstWeekday - 1 + index) % 7 + 1)
                    Text(day.getDisplayName(java.time.format.TextStyle.SHORT_STANDALONE, displayLocale), modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            repeat(rows) { week ->
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f))
                Row(Modifier.fillMaxWidth().height(rowHeight)) {
                    repeat(7) { col ->
                        val date = firstDate.plusDays((week * 7 + col).toLong())
                        val events = scheduled.filter { it.date == date.toString() }.sortedBy { it.startTime.ifBlank { "99:99" } }
                        val count = if (rowHeight >= 115.dp) 3 else if (rowHeight >= 82.dp) 2 else 1
                        Column(Modifier.weight(1f).fillMaxHeight().clickable { dayText = date.toString() }.padding(horizontal = 3.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.size(30.dp).background(if (date == LocalDate.now()) accent else Color.Transparent, CircleShape), contentAlignment = Alignment.Center) {
                                Text("${date.dayOfMonth}", color = if (date == LocalDate.now()) MaterialTheme.colorScheme.onPrimary else if (date.monthValue == month.monthValue && date.year == month.year) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .5f))
                            }
                            Spacer(Modifier.height(5.dp))
                            events.take(count).forEach { project ->
                                Text(eventLabel(project), Modifier.fillMaxWidth().padding(bottom = 3.dp).background(eventColor(project, accent).copy(alpha = .18f), RoundedCornerShape(3.dp)).padding(horizontal = 3.dp, vertical = 3.dp), fontSize = 10.sp, lineHeight = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            if (events.size > count) Text("+${events.size - count}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
    val day = dayText?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    if (day != null) ModalBottomSheet(onDismissRequest = { dayText = null }, containerColor = MaterialTheme.colorScheme.background) {
        val events = scheduled.filter { it.date == day.toString() }.sortedWith(compareBy<Project> { it.startTime.ifBlank { it.endTime.ifBlank { "99:99" } } }.thenBy { it.name })
        Column(Modifier.fillMaxWidth().heightIn(max = 620.dp).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(tr("当日日程"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { dayText = null }) { Text(tr("完成")) }
            }
            Text(day.format(DateTimeFormatter.ofPattern(tr("M月d日 EEEE"), displayLocale)), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(tr("{0} 项拍摄安排", events.size), color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (events.isEmpty()) Box(Modifier.fillMaxWidth().height(110.dp), contentAlignment = Alignment.Center) { Text(tr("当天没有拍摄安排"), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (events.any { it.startTime.isBlank() && it.endTime.isBlank() }) Text(tr("时间待定"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            events.forEach { project ->
                var menu by remember(project.id) { mutableStateOf(false) }
                Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.width(3.dp).height(68.dp).background(eventColor(project, accent), RoundedCornerShape(2.dp)))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(eventLabel(project), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    if (eventLabel(project) != project.name) Text(project.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Box { IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreHoriz, tr("日程操作")) }; DropdownMenu(menu, onDismissRequest = { menu = false }) {
                                    DropdownMenuItem(text = { Text(tr("编辑日历标签")) }, onClick = { menu = false; editingLabel = project.id })
                                    DropdownMenuItem(text = { Text(tr("调整日期与时间")) }, onClick = { menu = false; editingTime = project.id })
                                } }
                            }
                            val info = listOf(listOf(project.startTime, project.endTime).filter { it.isNotBlank() }.joinToString(" – "), project.location, project.clientName).filter { it.isNotBlank() }
                            info.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            TextButton(onClick = { dayText = null; opened = project.id }, contentPadding = PaddingValues(0.dp)) { Text(tr("打开拍摄脚本  ↗")) }
                        }
                    }
                }
            }
        }
    }
    editingLabel?.let { id -> store.projects.firstOrNull { it.id == id }?.let { project -> CalendarLabelEditor(project, { editingLabel = null }, { store.saveProject(it); editingLabel = null }) } }
    editingTime?.let { id -> store.projects.firstOrNull { it.id == id }?.let { project -> CalendarTimeEditor(project, { editingTime = null }, { store.saveProject(it); editingTime = null }) } }
}

@Composable
private fun CalendarLabelEditor(project: Project, onDismiss: () -> Unit, onSave: (Project) -> Unit) {
    var label by remember(project.id) { mutableStateOf(project.calendarLabelOverride.orEmpty()) }
    var color by remember(project.id) { mutableStateOf(project.calendarColorHex.orEmpty()) }
    val colors = listOf("#5F72D6", "#9570BB", "#677D52", "#C98842", "#BC6687")
    val valid = color.isEmpty() || Regex("#[0-9a-fA-F]{6}").matches(color)
    AlertDialog(onDismissRequest = onDismiss, title = { Text(tr("编辑日历标签")) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(tr("仅影响日历中的显示，不修改项目名称。"), style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(label, { label = it }, label = { Text(tr("日历短标签")) }, placeholder = { Text(project.name) }, singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { color = "" }) { Text(tr("默认")) }
                colors.forEach { hex -> Box(Modifier.size(32.dp).background(Color(android.graphics.Color.parseColor(hex)), CircleShape).clickable { color = hex }, contentAlignment = Alignment.Center) { if (color.equals(hex, true)) Text("✓", color = Color.White) } }
            }
            OutlinedTextField(color, { color = it }, label = { Text(tr("自选颜色 #RRGGBB")) }, isError = !valid, singleLine = true)
        }
    }, confirmButton = { TextButton(enabled = valid, onClick = { onSave(project.copy(calendarLabelOverride = label.trim().ifEmpty { null }, calendarColorHex = color.ifEmpty { null })) }) { Text(tr("保存")) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(tr("取消")) } })
}

@Composable
private fun CalendarTimeEditor(project: Project, onDismiss: () -> Unit, onSave: (Project) -> Unit) {
    var date by remember(project.id) { mutableStateOf(project.date) }
    var start by remember(project.id) { mutableStateOf(project.startTime) }
    var end by remember(project.id) { mutableStateOf(project.endTime) }
    val dateValid = runCatching { LocalDate.parse(date) }.isSuccess
    fun timeValid(value: String) = value.isEmpty() || (Regex("[0-2]\\d:[0-5]\\d").matches(value) && value.substringBefore(':').toInt() < 24)
    val valid = dateValid && timeValid(start) && timeValid(end) && (start.isEmpty() || end.isEmpty() || start < end)
    AlertDialog(onDismissRequest = onDismiss, title = { Text(tr("调整日期与时间")) }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(date, { date = it }, label = { Text(tr("日期 yyyy-MM-dd")) }, isError = !dateValid, singleLine = true)
        OutlinedTextField(start, { start = it }, label = { Text(tr("开始时间 HH:mm")) }, isError = !timeValid(start), singleLine = true)
        OutlinedTextField(end, { end = it }, label = { Text(tr("结束时间 HH:mm")) }, isError = !timeValid(end), singleLine = true)
        if (!valid) Text(tr("请输入有效日期和时间，结束时间需晚于开始时间。"), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    } }, confirmButton = { TextButton(enabled = valid, onClick = { onSave(project.copy(date = date, startTime = start, endTime = end)) }) { Text(tr("保存")) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(tr("取消")) } })
}
