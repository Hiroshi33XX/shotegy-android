// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Hiroshi33XX and contributors.
package com.shotegy.android

import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun FormSection(title: String, action: (@Composable () -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        if (title.isNotBlank() || action != null) Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(tr(title), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant); action?.invoke() }
        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface) { Column(Modifier.fillMaxWidth(), content = content) }
    }
}
@Composable
internal fun FormDivider() = HorizontalDivider(Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f), thickness = .5.dp)
@Composable
internal fun FormText(value: String, onChange: (String) -> Unit, placeholder: String, multiline: Boolean = false) {
    BasicTextField(value, onChange, modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 46.dp).padding(horizontal = 16.dp, vertical = 13.dp), textStyle = MaterialTheme.typography.bodyLarge.merge(TextStyle(color = MaterialTheme.colorScheme.onSurface)), singleLine = !multiline, decorationBox = { inner -> Box { if (value.isEmpty()) Text(tr(placeholder), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .65f), style = MaterialTheme.typography.bodyLarge); inner() } })
}
@Composable
internal fun FormAction(title: String, value: String = "", onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 46.dp).padding(horizontal = 16.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) { Text(tr(title), Modifier.weight(1f), color = MaterialTheme.colorScheme.primary); if (value.isNotEmpty()) Text(value, Modifier.weight(1f), maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.End, color = MaterialTheme.colorScheme.onSurfaceVariant); Icon(Icons.Outlined.ChevronRight, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
}
@Composable
internal fun FormPicker(title: String, value: String, options: List<String>, onChange: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val localizeOptions = title != "小场景"
    Row(Modifier.fillMaxWidth().clickable { open = true }.heightIn(min = 46.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(tr(title), Modifier.weight(1f))
        Spacer(Modifier.width(16.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            // The popup uses its immediate parent's bounds. Anchor it to the
            // value and chevron, rather than to the full-width form row.
            Box {
                Row(Modifier.heightIn(min = 46.dp).padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (value.isBlank()) tr("未分类") else if (localizeOptions) tr(value) else value, Modifier.weight(1f, fill = false), textAlign = androidx.compose.ui.text.style.TextAlign.End, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(5.dp))
                    Icon(Icons.Outlined.UnfoldMore, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                DropdownMenu(open, onDismissRequest = { open = false }) { options.forEach { option -> DropdownMenuItem(text = { Text(if (option.isBlank()) tr("未分类") else if (localizeOptions) tr(option) else option) }, onClick = { onChange(option); open = false }) } }
            }
        }
    }
}
@Composable
internal fun FormSegments(options: List<String>, selected: String, enabled: Boolean = true, onChange: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.onSurface.copy(alpha = .07f), RoundedCornerShape(8.dp)).padding(2.dp)) { options.forEach { option -> Box(Modifier.weight(1f).background(if (option == selected) MaterialTheme.colorScheme.surface else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(6.dp)).selectable(selected = option == selected, enabled = enabled, role = Role.Tab, onClick = { onChange(option) }).heightIn(min = 32.dp).padding(vertical = 6.dp), contentAlignment = Alignment.Center) { Text(trMessage(option), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else .38f), maxLines = 1) } } }
}

@Composable
internal fun FormTimeEditor(start: String, end: String, onSave: (String, String) -> Unit, onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var from by remember { mutableStateOf(start) }; var to by remember { mutableStateOf(end) }
    fun choose(value: String, update: (String) -> Unit) {
        val parts = value.split(":"); val hour = parts.getOrNull(0)?.toIntOrNull() ?: 9; val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
        android.app.TimePickerDialog(context, { _, h, m -> update("%02d:%02d".format(java.util.Locale.US, h, m)) }, hour, minute, true).show()
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(tr("建议时间")) }, text = { Column {
        FormAction("开始时间", from.ifBlank { tr("未设置") }) { choose(from) { from = it } }
        FormAction("结束时间", to.ifBlank { tr("未设置") }) { choose(to) { to = it } }
        TextButton(onClick = { from = ""; to = "" }) { Text(tr("清除时间")) }
        if (from.isNotBlank() && to.isNotBlank() && from >= to) Text(tr("结束时间需晚于开始时间"), color = MaterialTheme.colorScheme.error)
    } }, confirmButton = { TextButton(enabled = from.isBlank() || to.isBlank() || from < to, onClick = { onSave(from, to) }) { Text(tr("完成")) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(tr("取消")) } })
}

@Composable
internal fun FormSwitch(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) { Text(tr(title), Modifier.weight(1f)); Switch(checked, onCheckedChange = onChange) }
}

@Composable
internal fun FormMonthCalendar(date: String, projects: List<Project>, onSelect: (String) -> Unit) {
    val displayLocale = LocalAppLanguage.current.locale
    val selected = java.time.LocalDate.parse(date)
    var monthText by remember { mutableStateOf(java.time.YearMonth.from(selected).toString()) }
    val month = java.time.YearMonth.parse(monthText)
    val firstDay = java.time.temporal.WeekFields.of(displayLocale).firstDayOfWeek.value
    val offset = (month.atDay(1).dayOfWeek.value - firstDay + 7) % 7
    Column(Modifier.padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { Text(tr("{0}年{1}月", month.year, month.monthValue), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall); TextButton(onClick = { monthText = month.minusMonths(1).toString() }) { Text("‹") }; TextButton(onClick = { monthText = month.plusMonths(1).toString() }) { Text("›") } }
        Row { repeat(7) { col -> Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { Text(java.time.DayOfWeek.of((firstDay - 1 + col) % 7 + 1).getDisplayName(java.time.format.TextStyle.NARROW, displayLocale), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
        repeat((offset + month.lengthOfMonth() + 6) / 7) { row -> Row {
            repeat(7) { col -> val day = row * 7 + col - offset + 1
                Column(Modifier.weight(1f).height(68.dp).padding(2.dp).background(if (day == selected.dayOfMonth && month == java.time.YearMonth.from(selected)) MaterialTheme.colorScheme.primary.copy(alpha = .1f) else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(7.dp)).then(if (day in 1..month.lengthOfMonth()) Modifier.clickable { onSelect(month.atDay(day).toString()) } else Modifier), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (day in 1..month.lengthOfMonth()) { Text(day.toString(), Modifier.padding(top = 5.dp), color = if (day == selected.dayOfMonth && month == java.time.YearMonth.from(selected)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium)
                        projects.filter { !it.archived && it.date == month.atDay(day).toString() }.take(2).forEach { Text(it.calendarLabelOverride?.takeIf { it.isNotBlank() } ?: it.name, Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primary.copy(alpha = .15f), RoundedCornerShape(3.dp)), maxLines = 1, fontSize = 9.sp, lineHeight = 12.sp) }
                    }
                }
            }
        } }
    }
}
