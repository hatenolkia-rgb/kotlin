package com.vinay.dayflow.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vinay.dayflow.data.Fmt
import com.vinay.dayflow.data.Task
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun CalendarScreen(vm: AppVm, onEdit: (Task) -> Unit, onAdd: (LocalDate) -> Unit) {
    val monthFmt = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())
    val sel = vm.selectedDay
    val dayTasks = vm.dayTasks

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { ScreenTitle("Calendar", "Plan ahead and see how each day went") }
        item {
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { vm.shiftMonth(-1) }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month")
                    }
                    Text(
                        vm.month.format(monthFmt),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { vm.shiftMonth(1) }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month")
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth()) {
                    listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").forEach {
                        Text(
                            it,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                val first = vm.month.atDay(1)
                val offset = first.dayOfWeek.value - 1
                val len = vm.month.lengthOfMonth()
                val rows = (offset + len + 6) / 7
                for (r in 0 until rows) {
                    Row(Modifier.fillMaxWidth()) {
                        for (c in 0..6) {
                            val dayNum = r * 7 + c - offset + 1
                            Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp)) {
                                if (dayNum in 1..len) {
                                    val d = vm.month.atDay(dayNum)
                                    DayCell(
                                        date = d,
                                        stats = vm.monthStats[d],
                                        selected = d == sel,
                                        isToday = d == vm.today,
                                        today = vm.today,
                                        onClick = { vm.selectDay(d) },
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Legend()
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(Fmt.dayLabel(sel, vm.today), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        Fmt.longDate(sel) + if (dayTasks.isNotEmpty()) "  ·  ${dayTasks.count { it.done }}/${dayTasks.size} done" else "",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { onAdd(sel) }) { Text("+ Add") }
            }
        }
        if (dayTasks.isEmpty()) {
            item { EmptyState("No tasks on this day.") }
        }
        items(dayTasks, key = { "c${it.id}" }) { t ->
            TaskRow(task = t, onToggle = { vm.toggle(t, sel) }, onOpen = { onEdit(t) })
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    stats: Pair<Int, Int>?,
    selected: Boolean,
    isToday: Boolean,
    today: LocalDate,
    onClick: () -> Unit,
) {
    val done = stats?.first ?: 0
    val total = stats?.second ?: 0
    val bg = when {
        selected -> MaterialTheme.colorScheme.primary
        isToday -> MaterialTheme.colorScheme.primaryContainer
        else -> Color.Transparent
    }
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    val statColor = when {
        total == 0 -> Color.Transparent
        done == total -> Accent.good
        date.isBefore(today) && done == 0 -> Accent.bad
        date.isBefore(today) -> Accent.warn
        else -> if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
    }
    Column(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "${date.dayOfMonth}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isToday || selected) FontWeight.Bold else FontWeight.Normal,
            color = fg,
        )
        if (total > 0) {
            Text(
                "$done/$total",
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (selected && done != total) MaterialTheme.colorScheme.onPrimary else statColor,
            )
        }
    }
}

@Composable
private fun Legend() {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        LegendDot(Accent.good, "All done")
        LegendDot(Accent.warn, "Partly")
        LegendDot(Accent.bad, "Missed")
        LegendDot(MaterialTheme.colorScheme.primary, "Planned")
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(8.dp).height(8.dp).clip(RoundedCornerShape(4.dp)).background(color))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
