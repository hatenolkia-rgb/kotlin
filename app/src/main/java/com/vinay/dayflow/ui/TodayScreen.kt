package com.vinay.dayflow.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.vinay.dayflow.data.Fmt
import com.vinay.dayflow.data.Task
import com.vinay.dayflow.tracking.Perms
import java.time.LocalTime

@Composable
fun TodayScreen(
    vm: AppVm,
    onEdit: (Task) -> Unit,
    onRequestActivity: () -> Unit,
    onRequestNotifications: () -> Unit,
    onOpenInsights: () -> Unit,
) {
    val ctx = LocalContext.current
    val s = vm.todaySummary
    val p = vm.perms
    var quick by remember { mutableStateOf("") }
    val tasks = s?.tasks ?: emptyList()
    val doneCount = tasks.count { it.done }

    val hour = LocalTime.now().hour
    val greeting = when {
        hour < 12 -> "Good morning"
        hour < 17 -> "Good afternoon"
        else -> "Good evening"
    } + if (vm.name.isNotBlank()) ", ${vm.name}" else ""

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenTitle(greeting, Fmt.longDate(vm.today)) }

        // ----- Setup banners -----
        if (!p.activity && p.sensor) {
            item {
                ActionBanner(
                    "Count your steps",
                    "Allow physical-activity access so DayFlow can count your steps all day in the background.",
                    "Allow", onRequestActivity,
                )
            }
        }
        if (!p.usage) {
            item {
                ActionBanner(
                    "Track screen time & sleep",
                    "Turn on Usage access for DayFlow. It measures app usage and unlocks, and estimates sleep from how long your phone stays untouched at night. Nothing leaves your phone.",
                    "Open settings",
                ) { Perms.openUsageSettings(ctx) }
            }
        }
        if (!p.notifications) {
            item {
                ActionBanner(
                    "Task reminders",
                    "Allow notifications to get a reminder at each task's time.",
                    "Allow", onRequestNotifications,
                )
            }
        }

        // ----- Score -----
        item {
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ScoreRing(score = s?.score ?: 0, label = "score")
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(verdict(s?.score ?: 0), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            if (tasks.isEmpty()) "No tasks planned yet" else "$doneCount of ${tasks.size} tasks done",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Score updates through the day",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onClick = onOpenInsights) { Text("See full summary →") }
                    }
                }
            }
        }

        // ----- Metrics -----
        item {
            val steps = s?.steps
            val stepGoal = s?.stepGoal ?: vm.stepGoal
            val sleepMin = s?.sleepMin
            val sleepGoal = s?.sleepGoal ?: vm.sleepGoal
            val screen = s?.screenMin
            val limit = s?.screenLimit ?: vm.screenLimit
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MetricTile(
                        title = "Steps",
                        value = steps?.let { Fmt.num(it) } ?: "—",
                        sub = when {
                            !p.sensor -> "No step sensor on this phone"
                            !p.activity -> "Allow activity access"
                            else -> "Goal ${Fmt.num(stepGoal)}"
                        },
                        fraction = steps?.let { it.toFloat() / stepGoal },
                        color = if ((steps ?: 0) >= stepGoal) Accent.good else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    MetricTile(
                        title = "Sleep",
                        value = sleepMin?.let { Fmt.mins(it) } ?: "—",
                        sub = s?.sleep?.let { "${Fmt.clock(it.start)} – ${Fmt.clock(it.end)}" }
                            ?: if (!p.usage) "Needs usage access" else "Goal ${Fmt.mins(sleepGoal)}",
                        fraction = sleepMin?.let { it.toFloat() / sleepGoal },
                        color = if ((sleepMin ?: 0) >= sleepGoal - 30) Accent.good else Accent.warn,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MetricTile(
                        title = "Screen time",
                        value = screen?.let { Fmt.mins(it) } ?: "—",
                        sub = if (!p.usage) "Needs usage access"
                        else "Limit ${Fmt.mins(limit)}" + (s?.unlocks?.let { " · $it unlocks" } ?: ""),
                        fraction = screen?.let { it.toFloat() / limit },
                        color = if ((screen ?: 0) > limit) Accent.bad else MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.weight(1f),
                    )
                    MetricTile(
                        title = "Tasks",
                        value = "$doneCount/${tasks.size}",
                        sub = if (tasks.isEmpty()) "Plan your day" else "${s?.completion ?: 0}% complete",
                        fraction = if (tasks.isEmpty()) null else doneCount.toFloat() / tasks.size,
                        color = Accent.good,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        // ----- Quick add -----
        item {
            val submit: () -> Unit = {
                vm.quickAdd(quick)
                quick = ""
            }
            OutlinedTextField(
                value = quick,
                onValueChange = { quick = it },
                placeholder = { Text("Quick add a task for today…") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                trailingIcon = {
                    IconButton(onClick = { submit() }, enabled = quick.isNotBlank()) {
                        Icon(Icons.Filled.Add, contentDescription = "Add")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // ----- Overdue -----
        if (vm.overdue.isNotEmpty()) {
            item { SectionHeader("Overdue", "${vm.overdue.size}") }
            items(vm.overdue, key = { "o${it.id}" }) { t ->
                TaskRow(
                    task = t,
                    onToggle = { vm.toggle(t, null) },
                    onOpen = { onEdit(t) },
                    subtitle = Fmt.dayLabel(t.localDate),
                    action = { TextButton(onClick = { vm.moveToToday(t) }) { Text("Today") } },
                )
            }
        }

        // ----- Today's plan -----
        item { SectionHeader("Today's plan", if (tasks.isEmpty()) null else "$doneCount/${tasks.size}") }
        if (tasks.isEmpty()) {
            item { EmptyState("Nothing planned yet. Add your top 3 tasks for today.") }
        }
        items(tasks, key = { "t${it.id}" }) { t ->
            TaskRow(task = t, onToggle = { vm.toggle(t, vm.today) }, onOpen = { onEdit(t) })
        }

        // ----- Top tip -----
        val tip = s?.tips?.firstOrNull()
        if (tip != null) {
            item {
                AppCard {
                    Text("Tip for today", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(4.dp))
                    Text(tip, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
