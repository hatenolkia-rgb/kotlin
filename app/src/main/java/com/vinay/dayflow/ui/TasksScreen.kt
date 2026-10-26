package com.vinay.dayflow.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vinay.dayflow.data.Fmt
import com.vinay.dayflow.data.Task

@Composable
fun TasksScreen(vm: AppVm, onEdit: (Task) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
            ScreenTitle("To-do", "Tap a task to edit · tick to complete")
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TaskFilter.entries.forEach { f ->
                    FilterChip(
                        selected = vm.filter == f,
                        onClick = { vm.changeFilter(f) },
                        label = { Text(f.label) },
                    )
                }
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val secs = vm.sections
            if (secs.isEmpty() || secs.all { it.tasks.isEmpty() }) {
                item {
                    EmptyState(
                        when (vm.filter) {
                            TaskFilter.TODAY -> "No tasks for today. Tap + to add one."
                            TaskFilter.UPCOMING -> "Nothing scheduled for the next 7 days."
                            TaskFilter.ALL -> "No tasks yet. Tap + to create your first task."
                        }
                    )
                }
            }
            secs.forEach { sec ->
                if (sec.tasks.isEmpty()) return@forEach
                item(key = "h_${sec.title}") {
                    SectionHeader(sec.title, "${sec.tasks.count { it.done }}/${sec.tasks.size}")
                }
                items(sec.tasks, key = { "${sec.title}_${it.id}" }) { t ->
                    val sub = if (sec.date == null && !t.isRepeating) Fmt.dayLabel(t.localDate) else null
                    TaskRow(
                        task = t,
                        onToggle = { vm.toggle(t, sec.date) },
                        onOpen = { onEdit(t) },
                        subtitle = sub,
                    )
                }
            }
        }
    }
}
